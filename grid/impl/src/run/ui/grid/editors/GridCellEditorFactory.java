package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.extractors.TextInfo;
import com.intellij.database.remote.jdbc.GeoWrapper;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EventObject;

import static com.intellij.database.datagrid.GridUtil.createFormatterConfig;
import static com.intellij.database.extractors.DatabaseObjectFormatterConfig.isTypeAllowed;
import static com.intellij.database.extractors.ObjectFormatterUtil.toPresentableString;
import static com.intellij.openapi.vfs.CharsetToolkit.UTF8_BOM;

public interface GridCellEditorFactory {
  int SUITABILITY_UNSUITABLE = 0;
  int SUITABILITY_MIN = 1;
  int SUITABILITY_MAX = 10;

  int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  @NotNull
  IsEditableChecker getIsEditableChecker();

  @NotNull
  GridCellEditorFactory.ValueParser getValueParser(@NotNull DataGrid grid,
                                                   @NotNull ModelIndex<GridRow> rowIdx,
                                                   @NotNull ModelIndex<GridColumn> columnIdx);

  @NotNull
  GridCellEditorFactory.ValueFormatter getValueFormatter(@NotNull DataGrid grid,
                                                         @NotNull ModelIndex<GridRow> rowIdx,
                                                         @NotNull ModelIndex<GridColumn> columnIdx,
                                                         @Nullable Object value);

  @NotNull
  GridCellEditor createEditor(@NotNull DataGrid grid,
                              @NotNull ModelIndex<GridRow> row,
                              @NotNull ModelIndex<GridColumn> column,
                              @Nullable Object object,
                              EventObject initiator);

  interface IsEditableChecker {
    boolean isEditable(@Nullable Object value, @NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> column);
  }

  interface ValueParser {
    @NotNull Object parse(@NotNull String text, @Nullable Document document);
  }

  interface ValueFormatter {
    @NotNull ValueFormatterResult format();
  }

  class ValueFormatterResult {
    public final String text;
    public final Charset charset;
    public final byte[] bom;

    public ValueFormatterResult(String text) {
      this(text, StandardCharsets.UTF_8, UTF8_BOM);
    }

    public ValueFormatterResult(String text, Charset charset, byte[] bom) {
      this.text = text;
      this.charset = charset;
      this.bom = bom;
    }
  }


  class DefaultValueToText implements ValueFormatter {
    private final DataGrid myGrid;
    private final ModelIndex<GridColumn> myColumnIdx;
    private final Object myValue;

    public DefaultValueToText(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> columnIdx, @Nullable Object value) {
      myGrid = grid;
      myColumnIdx = columnIdx;
      myValue = ObjectUtils.notNull(value, ReservedCellValue.NULL);
    }

    @Override
    public @NotNull ValueFormatterResult format() {
      if (myValue instanceof LobInfo.ClobInfo) {
        return new ValueFormatterResult(((LobInfo.ClobInfo)myValue).data);
      }
      if (myValue instanceof GeoWrapper) {
        return new ValueFormatterResult(((GeoWrapper)myValue).getWkt());
      }
      if (myValue instanceof TextInfo textInfo && isTypeAllowed(createFormatterConfig(myGrid, myColumnIdx), BinaryDisplayType.TEXT)) {
        @Nullable DisplayType displayType = myGrid.getPureDisplayType(myColumnIdx);
        if (textInfo.text.equals(toPresentableString(textInfo.bytes, ObjectUtils.tryCast(displayType, BinaryDisplayType.class)))) {
          int length = CharsetToolkit.getBOMLength(textInfo.bytes, textInfo.charset);
          return new ValueFormatterResult(textInfo.text, textInfo.charset, Arrays.copyOf(textInfo.bytes, length));
        }
      }
      GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
      GridColumn column = model.getColumn(myColumnIdx);
      DatabaseDisplayObjectFormatterConfig formatterConfig = createFormatterConfig(myGrid, myColumnIdx);
      String text = myValue instanceof ReservedCellValue || column == null ? "" :
                    StringUtil.notNullize(myGrid.getObjectFormatter().objectToString(myValue, column, formatterConfig));
      return new ValueFormatterResult(text);
    }
  }
}
