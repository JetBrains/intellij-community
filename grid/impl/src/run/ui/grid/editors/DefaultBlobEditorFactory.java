package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.TextInfo;
import com.intellij.database.run.actions.ChangeCellEditorFileEncodingAction;
import com.intellij.database.run.actions.LoadFileAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.EventObject;

import static com.intellij.database.datagrid.GridUtil.createFormatterConfig;
import static com.intellij.database.extractors.DatabaseObjectFormatterConfig.isTypeAllowed;

public class DefaultBlobEditorFactory implements GridCellEditorFactory {
  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return switch (GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column)) {
      case Types.BINARY, Types.BLOB, Types.LONGVARBINARY, Types.VARBINARY -> SUITABILITY_MIN;
      default -> SUITABILITY_UNSUITABLE;
    };
  }

  @Override
  public @NotNull ValueFormatter getValueFormatter(@NotNull DataGrid grid,
                                                   @NotNull ModelIndex<GridRow> rowIdx,
                                                   @NotNull ModelIndex<GridColumn> columnIdx,
                                                   @Nullable Object value) {
    return new DefaultValueToText(grid, columnIdx, value);
  }

  @Override
  public @NotNull IsEditableChecker getIsEditableChecker() {
    return (value, grid, column) -> {
      TextInfo textInfo = ObjectUtils.tryCast(value, TextInfo.class);
      return value == null || textInfo != null && isTypeAllowed(createFormatterConfig(grid, column), BinaryDisplayType.TEXT);
    };
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    return (text, document) -> {
      VirtualFile file = document == null ? null : FileDocumentManager.getInstance().getFile(document);
      Charset charset = file == null ? StandardCharsets.UTF_8 : file.getCharset();
      byte[] bytes = text.getBytes(charset);
      byte[] bom = file == null ? null : file.getBOM();
      return new TextInfo(text, bom == null ? bytes : ArrayUtil.mergeArrays(bom, bytes), charset);
    };
  }

  @Override
  public @NotNull GridCellEditor createEditor(@NotNull DataGrid grid,
                                              @NotNull ModelIndex<GridRow> row,
                                              @NotNull ModelIndex<GridColumn> column,
                                              @Nullable Object object,
                                              EventObject initiator) {
    return new BlobTextCellEditor(grid, row, column, object, initiator, getIsEditableChecker(), getValueParser(grid, row, column), getValueFormatter(grid, row, column, object));
  }

  private static class BlobTextCellEditor extends GridTextCellEditorBase implements LoadFileAction.LoadFileActionHandler {
    private final ValueParser myValueParser;

    BlobTextCellEditor(@NotNull DataGrid grid,
                       @NotNull ModelIndex<GridRow> row,
                       @NotNull ModelIndex<GridColumn> column,
                       @Nullable Object value,
                       EventObject initiator,
                       @NotNull IsEditableChecker editableChecker,
                       @NotNull ValueParser valueParser,
                       @NotNull ValueFormatter valueFormatter) {
      super(grid, row, column, value, initiator, editableChecker, valueFormatter);
      myValueParser = valueParser;
    }

    @Override
    public @Nullable Object getValue() {
      return isValueEditable() ? myValueParser.parse(myTextField.getText(), myTextField.getDocument()) : myValue;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(LoadFileAction.LOAD_FILE_ACTION_HANDLER_KEY, this);
      if (isValueEditable()) {
        sink.set(ChangeCellEditorFileEncodingAction.ENCODING_CHANGE_SUPPORTED_KEY, Boolean.TRUE);
      }
      sink.set(CommonDataKeys.VIRTUAL_FILE, getVirtualFile());
      sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{getVirtualFile()});
    }

    @Override
    public void fileChosen(@NotNull VirtualFile file) {
      myValue = GridUtil.blobFromFile(file);
      myGrid.stopEditing();
    }

    private VirtualFile getVirtualFile() {
      return FileDocumentManager.getInstance().getFile(myTextField.getDocument());
    }
  }
}
