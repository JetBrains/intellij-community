package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel;
import com.intellij.database.datagrid.NestedTable;
import com.intellij.database.run.ui.grid.editors.FormatsCache;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class NestedTableAwareObjectFormatter extends BaseObjectFormatter {
  public NestedTableAwareObjectFormatter() {
    this(new FormatsCache(), new FormatterCreator());
  }

  public NestedTableAwareObjectFormatter(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    super(formatsCache, formatterCreator);
  }

  {
    myToString.register(NestedTable.class, new Converter<>() {
      @Override
      public String convert(NestedTable o, GridColumn column, ObjectFormatterConfig config) {
        ObjectFormatterMode itemsMode = config.getMode() == ObjectFormatterMode.SQL_SCRIPT
                                        ? ObjectFormatterMode.SQL_SCRIPT
                                        : ObjectFormatterMode.JSON;

        return JsonUtilKt.toJson(o, NestedTableAwareObjectFormatter.this, itemsMode, false, true, false);
      }
    });

    myToString.register(ImageInfo.class, new Converter<>() {
      @Override
      public String convert(ImageInfo o, GridColumn column, ObjectFormatterConfig config) {
        if (config.getMode() == ObjectFormatterMode.DISPLAY || o.bytes == null) {
          return o.width + "x" + o.height + " " + StringUtil.toUpperCase(o.format) + " image " + StringUtil.formatFileSize(o.size);
        }

        return o.toString();
      }
    });
  }

  @Override
  public boolean isStringLiteral(@Nullable GridColumn column, @Nullable Object value, @NotNull ObjectFormatterMode mode) {
    if (mode == ObjectFormatterMode.JSON) {
      return !(value instanceof Number || value instanceof Boolean || value instanceof Map || value instanceof List);
    }

    if (mode == ObjectFormatterMode.SQL_SCRIPT) {
      if (value instanceof NestedTable) return true;
      if (value instanceof Map && column instanceof HierarchicalColumnsDataGridModel.ExtractorHierarchicalGridColumn) return true;
    }

    return value instanceof String;
  }
}
