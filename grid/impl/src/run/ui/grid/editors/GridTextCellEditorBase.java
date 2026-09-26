package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.ActualGridCellRequest;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridCellRequestKt;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.ModelIndex;
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

import javax.swing.JComponent;
import java.util.EventObject;

public abstract class GridTextCellEditorBase extends GridCellEditor.Adapter implements UiDataProvider, GridCellEditor.EditorBased {
  private final IsEditableChecker myEditableChecker;
  private final ActualGridCellRequest<GridRow, GridColumn> myOriginalRequest;
  protected final GridCellEditorTextField myTextField;

  protected Object myValue;

  protected GridTextCellEditorBase(@NotNull GridCellRequest<GridRow, GridColumn> request,
                                   EventObject initiator,
                                   @NotNull IsEditableChecker editableChecker,
                                   @NotNull GridCellEditorFactory.ValueFormatter valueFormatter) {
    myEditableChecker = editableChecker;
    myOriginalRequest = GridCellRequestKt.actual(request);
    myValue = request.getValue();
    TextCompletionProvider provider = GridUtil.createCompletionProvider(request);

    var settings = GridUtil.getSettings(getGrid());
    boolean autoPopup = settings == null || settings.isEnableImmediateCompletionInGridCells();

    myTextField = new MyGridCellEditorTextField(initiator, provider, request, valueFormatter, autoPopup);
    Disposer.register(this, myTextField);
  }

  @NotNull
  public DataGrid getGrid() {
    return (DataGrid)myOriginalRequest.getGrid();
  }

  @NotNull
  public ModelIndex<GridColumn> getColumnIdx() {
    return myOriginalRequest.getColumnIdx();
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
    return myEditableChecker.isEditable(myValue, getGrid(), getColumnIdx());
  }

  private class MyGridCellEditorTextField extends GridCellEditorTextField {
    MyGridCellEditorTextField(EventObject initiator,
                              @Nullable TextCompletionProvider provider,
                              @NotNull GridCellRequest<GridRow, GridColumn> request,
                              @NotNull GridCellEditorFactory.ValueFormatter valueFormatter,
                              boolean autoPopup) {
      super(request.getGrid().getProject(), request, true, initiator, provider, autoPopup, valueFormatter);
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
