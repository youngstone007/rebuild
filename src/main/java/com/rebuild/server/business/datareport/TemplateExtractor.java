/*
rebuild - Building your business-systems freely.
Copyright (C) 2019 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.business.datareport;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.dialect.FieldType;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.metadata.Sheet;
import com.rebuild.server.RebuildException;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.server.metadata.entity.EasyMeta;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模板处理
 *
 * @author devezhao
 * @since 2019/8/16
 */
public class TemplateExtractor {

    private File template;

    public TemplateExtractor(File template) {
        this.template = template;
    }

    /**
     * 提取变量
     *
     * @param matchsAny
     * @return
     */
    public Set<String> extractVars(boolean matchsAny) {
        List<Object> rows = null;
        try (InputStream is = new FileInputStream(this.template)) {
            rows = EasyExcelFactory.read(is, new Sheet(1, 0));
        } catch (IOException ex) {
            throw new RebuildException(ex);
        }

        String regex = "\\$\\{[0-9a-zA-Z\\.]+\\}";
        // 能够匹配中文
        if (matchsAny) {
            regex = "\\$\\{.+\\}";
        }

        Set<String> vars = new HashSet<>();
        for (Object row : rows) {
            List<?> list = (List<?>) row;
            for (Object cell : list) {
                if (cell != null && cell.toString().matches(regex)) {
                    String cellVar = cell.toString();
                    cellVar = cellVar.substring(2, cellVar.length() - 1);
                    vars.add(cellVar);
                }
            }
        }
        return vars;
    }

    /**
     * @param entity
     * @return
     */
    public Map<String, String> transformVars(Entity entity) {
        Set<String> vars = extractVars(true);

        Map<String, String> map = new HashMap<>();
        for (String field : vars) {
            if (MetadataHelper.getLastJoinField(entity, field) != null) {
                map.put(field, field);
            } else {
                String realField = getRealField(entity, field);
                map.put(field, realField);  // Maybe null
            }
        }
        return map;
    }

    /**
     * @param entity
     * @param fieldPath
     * @return
     */
    protected String getRealField(Entity entity, String fieldPath) {
        String[] paths = fieldPath.split("\\.");
        List<String> realPaths = new ArrayList<>();

        Field lastField = null;
        Entity father = entity;
        for (String field : paths) {
            if (father == null) {
                return null;
            }

            lastField = getFieldByLabel(father, field);
            if (lastField == null) {
                return null;
            }

            realPaths.add(lastField.getName());

            if (lastField.getType() == FieldType.REFERENCE) {
                father = lastField.getReferenceEntity();
            } else {
                father = null;
            }
        }
        return StringUtils.join(realPaths, ".");
    }

    /**
     * @param entity
     * @param fieldLabel
     * @return
     */
    private Field getFieldByLabel(Entity entity, String fieldLabel) {
        for (Field field : entity.getFields()) {
            if (EasyMeta.getLabel(field).equalsIgnoreCase(fieldLabel)) {
                return field;
            }
        }
        return null;
    }
}
