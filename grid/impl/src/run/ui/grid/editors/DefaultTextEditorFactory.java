package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.DocumentDataHookUp;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.actions.LoadFileAction;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.util.LobInfoHelper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.Types;
import java.util.EventObject;

public class DefaultTextEditorFactory implements GridCellEditorFactory {

  @Override
  public int getSuitability(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    if (request.getGrid().getDataHookup() instanceof DocumentDataHookUp) return SUITABILITY_MAX; // allow text in numeric CSV columns
    return getCommonSuitability(request);
  }

  @Override
  public @NotNull ValueFormatter getValueFormatter(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    return getValueFormatter((DataGrid)request.getGrid(), request.getColumnIdx(), request.getValue());
  }

  private static ValueFormatter getValueFormatter(@NotNull DataGrid grid,
                                                  @NotNull ModelIndex<GridColumn> columnIdx,
                                                  @Nullable Object value) {
    return new DefaultValueToText(grid, columnIdx, value);
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    Object initialValue = request.getValue();
    // Respect the editor opening value so editSelectedCellWithValue() can control the unchanged commit result.
    String initialText = getValueFormatter(request).format().text;
    return (text, document) -> {
      boolean valueChanged = !initialText.equals(text);
      return valueChanged ? text : ObjectUtils.notNull(initialValue, ReservedCellValue.NULL);
    };
  }

  @Override
  public @NotNull IsEditableChecker getIsEditableChecker() {
    return (value, grid, column) -> {
      return isEditable(value);
    };
  }

  protected static boolean isEditable(Object value) {
    if (value == null || value instanceof String || value instanceof ReservedCellValue ||
        value instanceof LobInfo && !((LobInfo<?>)value).isTruncated() &&
        (value instanceof LobInfo.ClobInfo || ((LobInfo<?>)value).length == 0) ||
        (value instanceof Object[] objArr && objArr.length < LobInfoHelper.MAX_ARRAY_SIZE)) {
      return true;
    }
    return false;
  }

  @Override
  public @NotNull GridCellEditor createEditor(@NotNull GridCellRequest<GridRow, GridColumn> request, EventObject initiator) {
    ValueParser parser = getValueParser(request);
    ValueFormatter formatter = getValueFormatter(request);
    return new GridTextCellEditor(request, initiator, getIsEditableChecker(), parser, formatter);
  }

  private static int getCommonSuitability(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    return switch (GridCellEditorHelper.get(request.getGrid()).guessJdbcTypeForEditing(request)) {
      case Types.NCHAR, Types.CHAR, Types.VARCHAR, Types.NVARCHAR, Types.CLOB, Types.NCLOB, Types.LONGVARCHAR,
        Types.LONGNVARCHAR, Types.SQLXML -> SUITABILITY_MIN;
      default -> SUITABILITY_UNSUITABLE;
    };
  }

  private static class GridTextCellEditor extends GridTextCellEditorBase implements LoadFileAction.LoadFileActionHandler {
    private static final Logger LOG = Logger.getInstance(GridTextCellEditor.class);

    private final ValueParser myValueParser;

    private GridTextCellEditor(@NotNull GridCellRequest<GridRow, GridColumn> request,
                               EventObject initiator,
                               @NotNull IsEditableChecker editableChecker,
                               @NotNull ValueParser valueParser,
                               @NotNull ValueFormatter valueFormatter) {
      super(request, initiator, editableChecker, valueFormatter);
      myValueParser = valueParser;
      if (request.getGrid().getDataHookup() instanceof DocumentDataHookUp && ObjectFormatterUtil.isNumericCell(request)) {
        myTextField.addSettingsProvider(editor -> GridUtil.configureNumericEditor(getGrid(), editor));
      }
    }

    @Override
    public @Nullable Object getValue() {
      return isValueEditable() ? myValueParser.parse(myTextField.getText(), myTextField.getDocument()) : myValue;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(LoadFileAction.LOAD_FILE_ACTION_HANDLER_KEY, this);
    }

    @Override
    public void fileChosen(@NotNull VirtualFile file) {
      myValue = loadValueFromFile(file, GridUtil.getSettings(getGrid()));
      if (myValue == null || myValue instanceof LobInfo.FileClobInfo) {
        // either a file is too big or it has a binary format - no further editing available
        getGrid().stopEditing();
      }
      else {
        ValueFormatterResult result = getValueFormatter(getGrid(), getColumnIdx(), myValue).format();
        myTextField.setText(result.text);
      }
    }

    private static @Nullable Object loadValueFromFile(@NotNull VirtualFile virtualFile, @Nullable DataGridSettings settings) {
      if (settings != null && virtualFile.getLength() > settings.getBytesLimitPerValue()) {
        return GridUtil.clobFromFile(virtualFile);
      }

      try {
        return VfsUtilCore.loadText(virtualFile);
      }
      catch (IOException e) {
        String trace = ExceptionUtil.getUserStackTrace(e, LOG).replace("\n", "<br>\n"); //NON-NLS
        String title = DataGridBundle.message("notification.title.cannot.read.file", virtualFile.getName());
        Notifications.Bus.notify(new Notification("Update Table", title, trace, NotificationType.ERROR));
      }

      return null;
    }
  }
}
