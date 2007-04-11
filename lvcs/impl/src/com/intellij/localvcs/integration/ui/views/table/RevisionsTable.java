package com.intellij.localvcs.integration.ui.views.table;

import com.intellij.localvcs.integration.ui.models.HistoryDialogModel;
import com.intellij.util.ui.Table;

import javax.swing.plaf.TableUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseEvent;

public class RevisionsTable extends Table {
  private ShiftedCellRendererWrapper myShiftedCellRenderer = new ShiftedCellRendererWrapper();

  public RevisionsTable(HistoryDialogModel m) {
    super(new RevisionsTableModel(m));
    setDefaultRenderer(Object.class, new RevisionsTableCellRenderer());

    TableColumnModel cm = getColumnModel();

    cm.getColumn(0).setMinWidth(150);
    cm.getColumn(0).setMaxWidth(150);

    cm.getColumn(0).setResizable(false);
    cm.getColumn(1).setResizable(false);
  }

  public void setUI(TableUI ui) {
    getTableHeader().setReorderingAllowed(false);
    if (myShiftedCellRenderer != null) myShiftedCellRenderer.updateUI();
    super.setUI(new RevisionsTableUI(ui));
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    TableCellRenderer cellRenderer = super.getCellRenderer(row, column);
    if (column != getColumnCount() - 1 || cellRenderer == null) return cellRenderer;
    myShiftedCellRenderer.setDataRenderer(cellRenderer);
    return myShiftedCellRenderer;
  }

  public void repaint(long tm, int x, int y, int width, int height) {
    if (width == 0 || height == 0) return;
    Point bottomRight = new Point(x + width - 1, y + height - 1);
    int lastColumn = columnAtPoint(bottomRight);
    if ((lastColumn == -1 || lastColumn == getColumnCount() - 1) && getRowHeight() != 0) {
      int lastRow = rowAtPoint(bottomRight);
      if (lastRow == -1) lastRow = getRowCount() - 1;
      int lastHalf = getRowHeight(lastRow) / 2 + 1;
      int firstRow = rowAtPoint(new Point(bottomRight.x, y));
      if (firstRow == -1) firstRow = getRowCount() - 1;
      int firstHalf = getRowHeight(firstRow) / 2 + 1;
      y = Math.max(0, y - firstHalf);
      height += lastHalf + firstHalf;
    }
    super.repaint(tm, x, y, width, height);
  }

  protected void processMouseEvent(MouseEvent e) {
    int delta = correctEvent(e);
    super.processMouseEvent(e);
    e.translatePoint(0, -delta);
  }

  protected void processMouseMotionEvent(MouseEvent e) {
    int delta = correctEvent(e);
    super.processMouseMotionEvent(e);
    e.translatePoint(0, -delta);
  }

  private int correctEvent(MouseEvent e) {
    int column = columnAtPoint(e.getPoint());
    int delta = 0;
    if (column == getColumnCount() - 1) delta = -getRowHeight() / 2;
    e.translatePoint(0, delta);
    return delta;
  }

}