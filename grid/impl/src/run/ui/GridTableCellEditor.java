package com.intellij.database.run.ui;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.grid.ResizableCellEditorsSupport;
import com.intellij.database.run.ui.grid.editors.GridCellEditor;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

public class GridTableCellEditor extends AbstractTableCellEditor {
  public static final String TABLE_CELL_EDITOR_PROPERTY = "tableCellEditor";
  public static final Key<EventObject> EDITING_STARTER_CLIENT_PROPERTY_KEY = Key.create("EventThatCausedEditingToStart");

  private final DataGrid myGrid;
  private final ModelIndex<GridRow> myRowIdx;
  private final ModelIndex<GridColumn> myColumnIdx;
  private final GridCellEditorFactory myEditorFactory;

  private GridCellEditor myEditor = null;

  public GridTableCellEditor(DataGrid grid,
                             ModelIndex<GridRow> rowIdx,
                             ModelIndex<GridColumn> columnIdx,
                             GridCellEditorFactory editorFactory) {
    myGrid = grid;
    myRowIdx = rowIdx;
    myColumnIdx = columnIdx;
    myEditorFactory = editorFactory;
  }

  public boolean shouldMoveFocus() {
    return myEditor == null || myEditor.shouldMoveFocus();
  }

  @TestOnly
  public GridCellEditor getEditor() {
    return myEditor;
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
    if (myEditorFactory == null) return null;

    if (myEditor == null) {
      EventObject e = ComponentUtil.getClientProperty(table, EDITING_STARTER_CLIENT_PROPERTY_KEY);
      myEditor = myEditorFactory.createEditor(myGrid, myRowIdx, myColumnIdx, value, e);
      myEditor.setEditingListener(object -> {
        myGrid.fireValueEdited(object);
        Rectangle r = calculateSelectedRect(table);
        if (r == null) return;
        table.revalidate();
        table.repaint(r);
      });
    }

    table.addPropertyChangeListener(TABLE_CELL_EDITOR_PROPERTY, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getOldValue() == GridTableCellEditor.this && evt.getNewValue() != GridTableCellEditor.this) {
          Disposer.dispose(myEditor);
          table.removePropertyChangeListener(TABLE_CELL_EDITOR_PROPERTY, this);
        }
      }
    });

    return myEditor.isColumnSpanAllowed() ? new GridCellEditorComponentWrapper(myEditor) : myEditor.getComponent();
  }

  @Override
  public Object getCellEditorValue() {
    return myEditor != null ? myEditor.getValue() : null;
  }

  public @Nullable String getCellEditorText() {
    return myEditor != null ? myEditor.getText() : null;
  }

  @Override
  public boolean isCellEditable(EventObject e) {
    if (myEditorFactory == null) return false;

    if (e instanceof MouseEvent) {
      return ((MouseEvent)e).getClickCount() >= 2;
    }

    return true;
  }

  @Override
  public boolean stopCellEditing() {
    GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    if (!myGrid.isEditable() &&
        !model.allValuesEqualTo(ModelIndexSet.forRows(myGrid, myRowIdx.asInteger()),
                                ModelIndexSet.forColumns(myGrid, myColumnIdx.asInteger()),
                                getCellEditorValue())) {
      showCannotApplyCellEditorChangesDialog();
      return false;
    }
    return myEditor != null && myEditor.stop() && super.stopCellEditing();
  }

  @Override
  public void cancelCellEditing() {
    if (myEditor != null) {
      myEditor.cancel();
    }
    super.cancelCellEditing();
  }

  private static @Nullable Rectangle calculateSelectedRect(@NotNull JTable table) {
    int[] rows = table.getSelectedRows();
    int[] columns = table.getSelectedColumns();
    Rectangle selected = null;
    for (int row : rows) {
      for (int column : columns) {
        Rectangle rect = table.getCellRect(row, column, true);
        if (selected == null) {
          selected = rect;
        }
        else {
          selected = selected.union(rect);
        }
      }
    }
    return selected;
  }

  private void showCannotApplyCellEditorChangesDialog() {
    Application application = ApplicationManager.getApplication();
    ModalityState gridModality = application.getModalityStateForComponent(myGrid.getPanel().getComponent());
    ModalityState currentModality = ModalityState.current();
    if (Comparing.equal(gridModality, currentModality) && myGrid.getPanel().getComponent().isShowing()) {
      GridUtil.showCannotApplyCellEditorChanges(myGrid);
    }
  }

  private static class GridCellEditorComponentWrapper extends JComponent implements ResizableCellEditorsSupport.ResizableCellEditor {
    private final GridCellEditor myEditor;

    private boolean myWidthChangeEnabled = true;
    private KeyEvent myCurrentEvent = null;

    GridCellEditorComponentWrapper(@NotNull GridCellEditor editor) {
      myEditor = editor;
      setLayout(new BorderLayout());
      add(editor.getComponent(), BorderLayout.CENTER);
      setFocusable(false);
    }

    @Override
    public void setWidthChangeEnabled(boolean b) {
      myWidthChangeEnabled = b;
    }

    @Override
    public @NotNull ResizableCellEditorsSupport.ResizableCellEditor.HorizontalResizeDirection getHorizontalResizeDirection() {
      if (myEditor instanceof GridCellEditor.EditorBased) {
        Editor editor = ((GridCellEditor.EditorBased)myEditor).getEditor();
        if (editor instanceof EditorImpl && ((EditorImpl)editor).isRightAligned()) return HorizontalResizeDirection.LEFT;
      }
      return HorizontalResizeDirection.RIGHT;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      super.setBounds(myWidthChangeEnabled ? x : getX(), y, myWidthChangeEnabled ? width : getWidth(), height);
    }

    @Override
    public void requestFocus() {
      if (getComponentCount() == 1) {
        IdeFocusManager.getGlobalInstance()
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getComponent(0), true));
      }
    }

    @Override
    protected final boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
      if (condition == WHEN_FOCUSED && myCurrentEvent != e && getComponentCount() == 1) {
        try {
          myCurrentEvent = e;
          getComponent(0).dispatchEvent(e);
        }
        finally {
          myCurrentEvent = null;
        }
      }
      return e.isConsumed() || super.processKeyBinding(ks, e, condition, pressed);
    }
  }
}
