package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.IsEditableChecker;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.textCompletion.TextCompletionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EventObject;

public abstract class GridTextCellEditorBase extends GridCellEditor.Adapter implements UiDataProvider, GridCellEditor.EditorBased {
  protected final DataGrid myGrid;
  private final IsEditableChecker myEditableChecker;
  protected final GridColumn myColumn;
  protected final GridCellEditorTextField myTextField;

  protected Object myValue;

  protected GridTextCellEditorBase(@NotNull DataGrid grid,
                                   @NotNull ModelIndex<GridRow> row,
                                   @NotNull ModelIndex<GridColumn> column,
                                   @Nullable Object value,
                                   EventObject initiator,
                                   @NotNull IsEditableChecker editableChecker,
                                   @NotNull GridCellEditorFactory.ValueFormatter valueFormatter) {
    myGrid = grid;
    myEditableChecker = editableChecker;
    myColumn = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column);
    myValue = value;
    TextCompletionProvider provider = GridUtil.createCompletionProvider(grid, row, column);

    var settings = GridUtil.getSettings(myGrid);
    boolean autoPopup = settings == null || settings.isEnableImmediateCompletionInGridCells();

    myTextField = new MyGridCellEditorTextField(initiator, provider, row, column, valueFormatter, autoPopup);
    Disposer.register(this, myTextField);
  }

  @Override
  public @NotNull String getText() {
    return myTextField.getText();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myTextField;
  }

  @Override
  public @Nullable Editor getEditor() {
    return myTextField.getEditor();
  }

  protected boolean isValueEditable() {
    return myEditableChecker.isEditable(myValue, myGrid, ModelIndex.forColumn(myGrid, myColumn.getColumnNumber()));
  }

  private class MyGridCellEditorTextField extends GridCellEditorTextField {
    MyGridCellEditorTextField(EventObject initiator,
                              @Nullable TextCompletionProvider provider,
                              @NotNull ModelIndex<GridRow> row,
                              @NotNull ModelIndex<GridColumn> column,
                              @NotNull GridCellEditorFactory.ValueFormatter valueFormatter,
                              boolean autoPopup) {
      super(myGrid.getProject(), myGrid, row, column, true, initiator, provider, autoPopup, valueFormatter);
      getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          fireEditing(getDocument().getText());
        }
      });
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      DataSink.uiDataSnapshot(sink, GridTextCellEditorBase.this);
    }

    @Override
    protected boolean isEditable() {
      return super.isEditable() && isValueEditable();
    }
  }
}
