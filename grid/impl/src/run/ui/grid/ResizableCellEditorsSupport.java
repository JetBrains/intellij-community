package com.intellij.database.run.ui.grid;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.TableCell;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ResizableCellEditorsSupport implements PropertyChangeListener, TableColumnModelListener {
  private static final String CLIENT_PROPERTY_KEY = "ResizableCellEditorsSupport";

  private final JBTable myGrid;

  private final Timer myResetEditingCellSizeTimer;
  private final Timer myCellEditorUpdateTimer;

  private int myRowHeightBeforeEditing;
  private int myCalculatedRowHeight;

  public ResizableCellEditorsSupport(@NotNull JBTable grid) {
    myGrid = grid;

    myCellEditorUpdateTimer = TimerUtil.createNamedTimer("CellEditorUpdateTimer", 100, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        expandCellEditorIfNeeded(myGrid.getEditingRow(), myGrid.getEditingColumn(), false);
      }
    });

    myResetEditingCellSizeTimer = TimerUtil.createNamedTimer("ResetEditingCellSizeTimer", UIUtil.getMultiClickInterval());
    myResetEditingCellSizeTimer.setRepeats(false);

    myGrid.addPropertyChangeListener(this);
    addColumnModelListener(myGrid.getColumnModel());

    grid.putClientProperty(CLIENT_PROPERTY_KEY, this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    String property = evt.getPropertyName();

    if ("tableCellEditor".equals(property)) {
      if (evt.getNewValue() != null) {
        editingStarted();
      }
      else {
        editingStopped();
      }
    }
    else if ("columnModel".equals(property)) {
      removeColumnModelListener(ObjectUtils.tryCast(evt.getOldValue(), TableColumnModel.class));
      addColumnModelListener(ObjectUtils.tryCast(evt.getNewValue(), TableColumnModel.class));
    }
  }

  @Override
  public void columnAdded(TableColumnModelEvent e) {
  }

  @Override
  public void columnRemoved(TableColumnModelEvent e) {
  }

  @Override
  public void columnMoved(TableColumnModelEvent e) {
  }

  @Override
  public void columnMarginChanged(ChangeEvent e) {
    ApplicationManager.getApplication().invokeLater(() -> updateCellEditorWidth(myGrid.getEditingColumn(), true));
  }

  @Override
  public void columnSelectionChanged(ListSelectionEvent e) {
  }

  public static ResizableCellEditorsSupport get(@Nullable JTable table) {
    return table != null ? ObjectUtils.tryCast(table.getClientProperty(CLIENT_PROPERTY_KEY), ResizableCellEditorsSupport.class) : null;
  }

  private void editingStarted() {
    int editingColumn = myGrid.getEditingColumn();
    int editingRow = myGrid.getEditingRow();

    // At this point editing row and column can be unset in JTable,
    // but cell editor is already in place, so we can figure out which cell we are editing.
    if (editingColumn == -1 || editingRow == -1) {
      Component cellEditor = myGrid.getEditorComponent();
      Point editorLocation = cellEditor != null ? cellEditor.getBounds().getLocation() : null;
      if (editorLocation != null) {
        editingColumn = myGrid.columnAtPoint(editorLocation);
        editingRow = myGrid.rowAtPoint(editorLocation);
      }
    }

    if (editingRow != -1 && editingColumn != -1) {
      stopResetEditingRowHeightTimer();

      myRowHeightBeforeEditing = myGrid.getRowHeight(editingRow);

      expandCellEditorIfNeeded(editingRow, editingColumn, true);
      scrollToCellEditor();
      myCellEditorUpdateTimer.start();
    }
  }

  private void editingStopped() {
    myCellEditorUpdateTimer.stop();
    resetEditingCellSize();
  }

  private void scrollToCellEditor() {
    ApplicationManager.getApplication().invokeLater(() -> {
      Component cellEditor = myGrid.getEditorComponent();
      if (cellEditor == null || !cellEditor.isVisible()) return;

      Rectangle editorBounds = cellEditor.getBounds();
      Rectangle visibleRect = myGrid.getVisibleRect();
      if (!visibleRect.contains(editorBounds)) {
        int margin = myGrid.getRowHeight();
        editorBounds.x -= margin;
        editorBounds.y -= margin;
        editorBounds.width += 2 * margin;
        editorBounds.height += 2 * margin;
        myGrid.scrollRectToVisible(editorBounds);
      }
    });
  }

  private void expandCellEditorIfNeeded(int editingRow, int editingColumn, boolean force) {
    updateCellEditorWidth(editingColumn, force);
    updateEditingRowHeight(editingRow, force);
  }

  private void updateCellEditorWidth(int editingColumn, boolean force) {
    Component cellEditor = myGrid.getEditorComponent();
    if (cellEditor == null || editingColumn < 0) return;

    int preferredWidth = cellEditor.getPreferredSize().width + myGrid.getColumnModel().getColumnMargin();
    int currentWidth = cellEditor.getWidth();
    if (!force && currentWidth >= preferredWidth) return;

    ResizableCellEditor resizable = ObjectUtils.tryCast(myGrid.getEditorComponent(), ResizableCellEditor.class);
    Rectangle currentRect = myGrid.getCellRect(0, editingColumn, false);
    Rectangle newRect = resizable != null ?
                        resizable.getHorizontalResizeDirection().resize(myGrid, currentRect, editingColumn, preferredWidth) :
                        currentRect;
    if (force && currentWidth != newRect.width || currentWidth < newRect.width) {
      Rectangle bounds = cellEditor.getBounds();
      bounds.width = newRect.width;
      bounds.x = newRect.x;
      setCellEditorBounds(cellEditor, bounds);
    }
  }

  private static void setCellEditorBounds(@NotNull Component cellEditor, @NotNull Rectangle bounds) {
    ResizableCellEditor widthChangeSupport = ObjectUtils.tryCast(cellEditor, ResizableCellEditor.class);
    if (widthChangeSupport == null) return;
    widthChangeSupport.setWidthChangeEnabled(true);
    try {
      cellEditor.setBounds(bounds);
    }
    finally {
      widthChangeSupport.setWidthChangeEnabled(false);
    }
  }

  private void updateEditingRowHeight(int editingRow, boolean force) {
    Component editor = myGrid.getEditorComponent();
    if (editor != null && editingRow >= 0) {
      int newRowHeight = Math.min(10 * myGrid.getRowHeight(), editor.getPreferredSize().height + myGrid.getRowMargin());
      int currentRowHeight = myGrid.getRowHeight(editingRow);
      if (force || myCalculatedRowHeight == currentRowHeight) {
        if (currentRowHeight < newRowHeight) {
          setCellSize(editingRow, newRowHeight);
        }
        myCalculatedRowHeight = newRowHeight;
      }
    }
  }

  private void stopResetEditingRowHeightTimer() {
    myResetEditingCellSizeTimer.stop();
    for (ActionListener listener : myResetEditingCellSizeTimer.getActionListeners()) {
      listener.actionPerformed(null);
    }
  }

  private void resetEditingCellSize() {
    final int row = myGrid.getEditingRow();
    final int height = myRowHeightBeforeEditing;
    if (IdeEventQueue.getInstance().getTrueCurrentEvent() instanceof MouseEvent) {
      // Introduces a delay before restoring editing cell sizes.
      // It allows user to double-click on a cell below and to the right of currently edited cell.
      myResetEditingCellSizeTimer.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setCellSize(row, height);
          myResetEditingCellSizeTimer.removeActionListener(this);
        }
      });
      myResetEditingCellSizeTimer.start();
    }
    else {
      setCellSize(row, height);
    }
  }

  private void setCellSize(int row, int height) {
    hideExpansionHint();
    if (row != -1 && height != -1) {
      myGrid.setRowHeight(row, height);
    }
  }

  private void hideExpansionHint() {
    ExpandableItemsHandler<TableCell> h = myGrid.getExpandableItemsHandler();
    if (h.isEnabled() && !h.getExpandedItems().isEmpty()) {
      h.setEnabled(false);
      h.setEnabled(true);
    }
  }

  private void addColumnModelListener(@Nullable TableColumnModel to) {
    if (to != null) {
      to.addColumnModelListener(this);
    }
  }

  private void removeColumnModelListener(@Nullable TableColumnModel from) {
    if (from != null) {
      from.removeColumnModelListener(this);
    }
  }

  public interface ResizableCellEditor {
    void setWidthChangeEnabled(boolean b);

    @NotNull
    HorizontalResizeDirection getHorizontalResizeDirection();

    enum HorizontalResizeDirection {
      LEFT {
        @NotNull
        @Override
        Rectangle resize(@NotNull JBTable table, @NotNull Rectangle current, int column, int preferredWidth) {
          Rectangle result = new Rectangle(current);
          int leftmostVisibleX = table.getVisibleRect().x;
          for (int currentColumn = column - 1; currentColumn > -1 && result.width < preferredWidth; currentColumn--) {
            Rectangle r = table.getCellRect(0, currentColumn, true);
            if (leftmostVisibleX <= r.x) {
              result.x = r.x;
              result.width += r.width;
            }
          }
          return result;
        }
      },
      RIGHT {
        @NotNull
        @Override
        Rectangle resize(@NotNull JBTable table, @NotNull Rectangle current, int column, int preferredWidth) {
          Rectangle result = new Rectangle(current);
          Rectangle rect = table.getVisibleRect();
          int rightmostVisibleX = rect.x + rect.width;
          for (int currentColumn = column + 1; currentColumn < table.getColumnCount() && result.width < preferredWidth; currentColumn++) {
            Rectangle r = table.getCellRect(0, currentColumn, true);
            if (rightmostVisibleX >= r.x + r.width) result.width += r.width;
          }
          return result;
        }
      };

      abstract @NotNull Rectangle resize(@NotNull JBTable table, @NotNull Rectangle current, int column, int preferredWidth);
    }
  }
}
