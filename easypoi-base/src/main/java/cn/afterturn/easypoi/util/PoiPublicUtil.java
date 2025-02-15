/**
 * Copyright 2013-2015 JueYue (qrb.jueyue@gmail.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.afterturn.easypoi.util;

import cn.afterturn.easypoi.cache.ImageCache;
import cn.afterturn.easypoi.entity.ImageEntity;
import cn.afterturn.easypoi.excel.annotation.Excel;
import cn.afterturn.easypoi.excel.annotation.ExcelCollection;
import cn.afterturn.easypoi.excel.annotation.ExcelEntity;
import cn.afterturn.easypoi.excel.annotation.ExcelIgnore;
import cn.afterturn.easypoi.word.entity.params.ExcelListEntity;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

import static cn.afterturn.easypoi.util.PoiElUtil.END_STR;
import static cn.afterturn.easypoi.util.PoiElUtil.START_STR;

/**
 * EASYPOI 的公共基础类
 *
 * @author JueYue
 * 2015年4月5日 上午12:59:22
 */
public final class PoiPublicUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoiPublicUtil.class);

    private PoiPublicUtil() {

    }

    @SuppressWarnings({"unchecked"})
    public static <K, V> Map<K, V> mapFor(Object... mapping) {
        Map<K, V> map = new HashMap<K, V>();
        for (int i = 0; i < mapping.length; i += 2) {
            map.put((K) mapping[i], (V) mapping[i + 1]);
        }
        return map;
    }

    /**
     * 彻底创建一个对象
     *
     * @param clazz
     * @return
     */
    public static Object createObject(Class<?> clazz, String targetId) {
        Object obj = null;
        try {
            if (clazz.equals(Map.class)) {
                return new LinkedHashMap<String, Object>();
            }
            obj = clazz.newInstance();
            Field[] fields = getClassFields(clazz);
            for (Field field : fields) {
                if (isNotUserExcelUserThis(null, field, targetId)) {
                    continue;
                }
                if (isCollection(field.getType())) {
                    ExcelCollection collection = field.getAnnotation(ExcelCollection.class);
                    PoiReflectorUtil.fromCache(clazz).setValue(obj, field.getName(),
                            collection.type().newInstance());
                } else if (!isJavaClass(field) && !field.getType().isEnum()) {
                    PoiReflectorUtil.fromCache(clazz).setValue(obj, field.getName(),
                            createObject(field.getType(), targetId));
                }
            }

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException("创建对象异常");
        }
        return obj;

    }

    /**
     * 获取class的 包括父类的
     *
     * @param clazz
     * @return
     */
    public static Field[] getClassFields(Class<?> clazz) {
        List<Field> list = new ArrayList<Field>();
        Field[]     fields;
        do {
            fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                list.add(fields[i]);
            }
            clazz = clazz.getSuperclass();
        } while (clazz != Object.class && clazz != null);
        return list.toArray(fields);
    }

    /**
     * @param photoByte
     * @return
     */
    public static String getFileExtendName(byte[] photoByte) {
        String strFileExtendName = "JPG";
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70)
                && (photoByte[3] == 56) && ((photoByte[4] == 55) || (photoByte[4] == 57))
                && (photoByte[5] == 97)) {
            strFileExtendName = "GIF";
        } else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73)
                && (photoByte[9] == 70)) {
            strFileExtendName = "JPG";
        } else if ((photoByte[0] == 66) && (photoByte[1] == 77)) {
            strFileExtendName = "BMP";
        } else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71)) {
            strFileExtendName = "PNG";
        }
        return strFileExtendName;
    }


    /**
     * 判断流是否含有BOM
     *
     * @param in
     * @return
     * @throws IOException
     */
    public static boolean hasBom(InputStream in) throws IOException {
        byte[] head = new byte[3];
        in.read(head);
        if (head[0] == -17 && head[1] == -69 && head[2] == -65) {
            return true;
        }
        return false;
    }

    /**
     * 获取Excel2003图片
     *
     * @param sheet    当前sheet对象
     * @param workbook 工作簿对象
     * @return Map key:图片单元格索引（1_1）String，value:图片流PictureData
     */
    public static Map<String, PictureData> getSheetPictrues03(HSSFSheet sheet,
                                                              HSSFWorkbook workbook) {
        Map<String, PictureData> sheetIndexPicMap = new HashMap<String, PictureData>();
        List<HSSFPictureData>    pictures         = workbook.getAllPictures();
        if (!pictures.isEmpty()) {
            for (HSSFShape shape : sheet.getDrawingPatriarch().getChildren()) {
                HSSFClientAnchor anchor = (HSSFClientAnchor) shape.getAnchor();
                if (shape instanceof HSSFPicture) {
                    HSSFPicture     pic          = (HSSFPicture) shape;
                    int             pictureIndex = pic.getPictureIndex() - 1;
                    HSSFPictureData picData      = pictures.get(pictureIndex);
                    String picIndex = String.valueOf(anchor.getRow1()) + "_"
                            + String.valueOf(anchor.getCol1());
                    sheetIndexPicMap.put(picIndex, picData);
                }
            }
            return sheetIndexPicMap;
        } else {
            return sheetIndexPicMap;
        }
    }

    /**
     * 获取Excel2007图片
     *
     * @param sheet    当前sheet对象
     * @param workbook 工作簿对象
     * @return Map key:图片单元格索引（1_1）String，value:图片流PictureData
     */
    public static Map<String, PictureData> getSheetPictrues07(XSSFSheet sheet,
                                                              XSSFWorkbook workbook) {
        Map<String, PictureData> sheetIndexPicMap = new HashMap<String, PictureData>();
        for (POIXMLDocumentPart dr : sheet.getRelations()) {
            if (dr instanceof XSSFDrawing) {
                XSSFDrawing     drawing = (XSSFDrawing) dr;
                List<XSSFShape> shapes  = drawing.getShapes();
                for (XSSFShape shape : shapes) {
                    if (shape instanceof XSSFPicture) {
                        XSSFPicture      pic      = (XSSFPicture) shape;
                        XSSFClientAnchor anchor   = pic.getPreferredSize();
                        CTMarker         ctMarker = anchor.getFrom();
                        String           picIndex = ctMarker.getRow() + "_" + ctMarker.getCol();
                        sheetIndexPicMap.put(picIndex, pic.getPictureData());
                    }
                }
            }
        }
        return sheetIndexPicMap;
    }

    /**
     * 判断是不是集合的实现类
     *
     * @param clazz
     * @return
     */
    public static boolean isCollection(Class<?> clazz) {
        return Collection.class.isAssignableFrom(clazz);
    }

    /**
     * 是不是java基础类
     *
     * @param field
     * @return
     */
    public static boolean isJavaClass(Field field) {
        Class<?> fieldType = field.getType();
        if(fieldType.isArray()){
            Class<?> fieldComponentType = field.getType().getComponentType();
            if (fieldType.isPrimitive() || fieldComponentType.getPackage() == null
                    || "java.lang".equals(fieldComponentType.getPackage().getName())
                    || "java.math".equals(fieldComponentType.getPackage().getName())
                    || "java.sql".equals(fieldComponentType.getPackage().getName())
                    || "java.util".equals(fieldComponentType.getPackage().getName())) {
                return true;
            }
            return false;
        }else {
            if (fieldType.isPrimitive() || fieldType.getPackage() == null
                    || "java.lang".equals(fieldType.getPackage().getName())
                    || "java.math".equals(fieldType.getPackage().getName())
                    || "java.sql".equals(fieldType.getPackage().getName())
                    || "java.util".equals(fieldType.getPackage().getName())) {
                return true;
            }
            return false;
        }
    }

    /**
     * 判断是否不要在这个excel操作中
     *
     * @param exclusionsList
     * @param field
     * @param targetId
     * @return
     */
    public static boolean isNotUserExcelUserThis(List<String> exclusionsList, Field field,
                                                 String targetId) {
        boolean boo = true;
        if (field.getAnnotation(ExcelIgnore.class) != null) {
            boo = true;
        } else if (boo && field.getAnnotation(ExcelCollection.class) != null
                && isUseInThis(field.getAnnotation(ExcelCollection.class).name(), targetId)
                && (exclusionsList == null || !exclusionsList
                .contains(field.getAnnotation(ExcelCollection.class).name()))) {
            boo = false;
        } else if (boo && field.getAnnotation(Excel.class) != null
                && isUseInThis(field.getAnnotation(Excel.class).name(), targetId)
                && (exclusionsList == null
                || !exclusionsList.contains(field.getAnnotation(Excel.class).name()))) {
            boo = false;
        } else if (boo && field.getAnnotation(ExcelEntity.class) != null
                && isUseInThis(field.getAnnotation(ExcelEntity.class).name(), targetId)
                && (exclusionsList == null || !exclusionsList
                .contains(field.getAnnotation(ExcelEntity.class).name()))) {
            boo = false;
        }
        return boo;
    }

    /**
     * 判断是不是使用
     *
     * @param exportName
     * @param targetId
     * @return
     */
    private static boolean isUseInThis(String exportName, String targetId) {
        return targetId == null || "".equals(exportName) || exportName.indexOf("_") < 0
                || exportName.indexOf(targetId) != -1;
    }

    private static Integer getImageType(String type) {
        if ("JPG".equalsIgnoreCase(type) || "JPEG".equalsIgnoreCase(type)) {
            return XWPFDocument.PICTURE_TYPE_JPEG;
        }
        if ("GIF".equalsIgnoreCase(type)) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        if ("BMP".equalsIgnoreCase(type)) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        if ("PNG".equalsIgnoreCase(type)) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }

    /**
     * 返回流和图片类型
     *
     * @param entity
     * @return (byte[]) isAndType[0],(Integer)isAndType[1]
     * @throws Exception
     * @author JueYue
     * 2013-11-20
     */
    public static Object[] getIsAndType(ImageEntity entity) throws Exception {
        Object[] result = new Object[2];
        String   type;
        if (entity.getType().equals(ImageEntity.URL)) {
            result[0] = ImageCache.getImage(entity.getUrl());
            type = entity.getUrl().split("/.")[entity.getUrl().split("/.").length - 1];
        } else {
            result[0] = entity.getData();
            type = PoiPublicUtil.getFileExtendName(entity.getData());
        }
        result[1] = getImageType(type);
        return result;
    }

    /**
     * 获取参数值
     *
     * @param params
     * @param object
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static Object getParamsValue(String params, Object object) throws Exception {
        if (params.indexOf(".") != -1) {
            String[] paramsArr = params.split("\\.");
            return getValueDoWhile(object, paramsArr, 0);
        }
        if (object instanceof Map) {
            return ((Map) object).get(params);
        }
        return PoiReflectorUtil.fromCache(object.getClass()).getValue(object, params);
    }

    /**
     * 解析数据
     *
     * @return
     * @author JueYue
     * 2013-11-16
     */
    public static Object getRealValue(String currentText,
                                      Map<String, Object> map) throws Exception {
        String params = "";
        while (currentText.indexOf(START_STR) != -1) {
            params = currentText.substring(currentText.indexOf(START_STR) + 2, currentText.indexOf(END_STR));
            Object obj = PoiElUtil.eval(params.trim(), map);
            //判断图片或者是集合
            if (obj instanceof ImageEntity || obj instanceof List || obj instanceof ExcelListEntity) {
                return obj;
            } else if (obj != null) {
                currentText = currentText.replace(START_STR + params + END_STR, obj.toString());
            } else {
                currentText = currentText.replace(START_STR + params + END_STR, "");
            }
        }
        return currentText;
    }

    /**
     * 通过遍历过去对象值
     *
     * @param object
     * @param paramsArr
     * @param index
     * @return
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    public static Object getValueDoWhile(Object object, String[] paramsArr,
                                         int index) throws Exception {
        if (object == null) {
            return "";
        }
        if (object instanceof ImageEntity) {
            return object;
        }
        if (object instanceof Map) {
            object = ((Map) object).get(paramsArr[index]);
        } else {
            object = PoiReflectorUtil.fromCache(object.getClass()).getValue(object,
                    paramsArr[index]);
        }

        if (object instanceof Collection) {
            return object;
        }
        return (index == paramsArr.length - 1) ? (object == null ? "" : object)
                : getValueDoWhile(object, paramsArr, ++index);
    }

    /**
     * double to String 防止科学计数法
     *
     * @param value
     * @return
     */
    public static String doubleToString(Double value) {
        String temp = value.toString();
        if (temp.contains("E")) {
            BigDecimal bigDecimal = new BigDecimal(temp);
            temp = bigDecimal.toPlainString();
        }
        return temp;
    }

    /**
     * 统一 key的获取规则
     *
     * @param key
     * @param targetId
     * @return
     */
    public static String getValueByTargetId(String key, String targetId, String defalut) {
        if (StringUtils.isEmpty(targetId) || key.indexOf("_") < 0) {
            return key;
        }
        String[] arr = key.split(",");
        String[] tempArr;
        for (String str : arr) {
            tempArr = str.split("_");
            if (tempArr == null || tempArr.length < 2) {
                return defalut;
            }
            if (targetId.equals(tempArr[1])) {
                return tempArr[0];
            }
        }
        return defalut;
    }

    /**
     * 支持换行操作
     *
     * @param currentRun
     * @param currentText
     */
    public static void setWordText(XWPFRun currentRun, String currentText) {
        if (StringUtils.isNotEmpty(currentText)) {
            String[] tempArr = currentText.split("\r\n");
            for (int i = 0, le = tempArr.length - 1; i < le; i++) {
                currentRun.setText(tempArr[i], i);
                currentRun.addBreak();
            }
            currentRun.setText(tempArr[tempArr.length - 1], tempArr.length - 1);
        } else {
            //对blank字符串做处理，避免显示"{{"
            currentRun.setText("", 0);
        }
    }

    public static int getNumDigits(int num) {
        int count = 0;
        while (num > 0) {
            num = num / 10;
            count++;
        }
        return count;
    }

    /**
     * 多个点,截取最后一个
     *
     * @param name
     * @return
     */
    public static String getLastFieldName(String name) {
        String[] paramsArr = name.split("\\.");
        return paramsArr[paramsArr.length - 1];
    }
}
