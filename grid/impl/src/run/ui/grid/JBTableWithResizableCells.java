package com.intellij.database.run.ui.grid;

import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JBTableWithResizableCells extends JBTable {
  private static final int RESIZE_AREA_RADIUS = 3;

  private CellRectangle myResizingCellOrigin;
  private boolean myExpandableItemsHandlerState;

  public JBTableWithResizableCells(TableModel model, TableColumnModel columnModel) {
    super(model, columnModel);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    preProcessMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseEvent(e);
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    preProcessMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseMotionEvent(e);
    }
  }

  private void preProcessMouseEvent(MouseEvent e) {
    if (e.isPopupTrigger()) return; // prevents

    Point mouseLocation = e.getPoint();
    CellRectangle cellToResize = getCellToResize(mouseLocation);
    int eventId = e.getID();

    if (eventId == MouseEvent.MOUSE_DRAGGED) {
      if (myResizingCellOrigin != null) {
        updateResizingCell(mouseLocation);
        e.consume();
      }
    }
    else if (eventId == MouseEvent.MOUSE_MOVED || eventId == MouseEvent.MOUSE_ENTERED || eventId == MouseEvent.MOUSE_EXITED) {
      updateCursor(cellToResize);
    }
    else if (eventId == MouseEvent.MOUSE_PRESSED) {
      if (cellToResize != null) {
        myExpandableItemsHandlerState = getExpandableItemsHandler().isEnabled();
        setExpandableItemsEnabled(false);
        myResizingCellOrigin = cellToResize;
        updateResizingCell(mouseLocation);
        e.consume();
      }
    }
    else if (eventId == MouseEvent.MOUSE_RELEASED) {
      if (myResizingCellOrigin != null) {
        setExpandableItemsEnabled(myExpandableItemsHandlerState);
        myResizingCellOrigin = null;
        e.consume();
      }
    }
  }

  private void updateCursor(@Nullable CellRectangle cellToResize) {
    int newCursorType = cellToResize != null || myResizingCellOrigin != null ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR;
    if (getCursor().getType() != newCursorType) {
      setCursor(Cursor.getPredefinedCursor(newCursorType));
    }
  }

  private void updateResizingCell(@NotNull Point mouseLocation) {
    int diffX = mouseLocation.x - myResizingCellOrigin.x - myResizingCellOrigin.width - 1;
    int diffY = mouseLocation.y - myResizingCellOrigin.y - myResizingCellOrigin.height - 1;
    int newWidth = myResizingCellOrigin.width + diffX;
    int newHeight = Math.max(getRowHeight(), myResizingCellOrigin.height + diffY);

    setRowHeight(myResizingCellOrigin.row, newHeight);
    getColumnModel().getColumn(myResizingCellOrigin.column).setPreferredWidth(newWidth);
  }

  private @Nullable CellRectangle getCellToResize(@NotNull Point mouseLocation) {
    int row = rowAtPoint(mouseLocation);
    int column = columnAtPoint(mouseLocation);

    Rectangle hoveredCell = row != -1 && column != -1 ? getCellRect(row, column, true) : null;
    if (hoveredCell == null) return null;

    // Resizable areas are circles of radius RESIZE_AREA_RADIUS located at grid intersections
    // so we may need to resize a neighboring cell and not the one we're hovering now.
    if (column != 0 && mouseLocation.distance(hoveredCell.x, hoveredCell.y + hoveredCell.height - 1) < RESIZE_AREA_RADIUS) {
      column -= 1;
    }
    else if (row != 0 && mouseLocation.distance(hoveredCell.x + hoveredCell.width - 1, hoveredCell.y) < RESIZE_AREA_RADIUS) {
      row -= 1;
    }
    else if (column != 0 && row != 0 && mouseLocation.distance(hoveredCell.x, hoveredCell.y) < RESIZE_AREA_RADIUS) {
      row -= 1;
      column -= 1;
    }

    Rectangle toResize = getCellRect(row, column, true);
    boolean inResizeArea = mouseLocation.distance(toResize.x + toResize.width - 1, toResize.y + toResize.height - 1) < RESIZE_AREA_RADIUS;
    return inResizeArea ? new CellRectangle(row, column, toResize) : null;
  }

  private static class CellRectangle extends Rectangle {
    public final int row;
    public final int column;

    CellRectangle(int row, int column, Rectangle r) {
      super(r);
      this.row = row;
      this.column = column;
    }
  }
}
