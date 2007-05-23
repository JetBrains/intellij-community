package com.intellij.localvcs.integration.ui.views.table;

import com.intellij.localvcs.integration.ui.models.HistoryDialogModel;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.TableUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseEvent;

public class RevisionsTable extends Table {
  private ShiftedCellRendererWrapper myShiftedCellRenderer = new ShiftedCellRendererWrapper();

  public RevisionsTable(HistoryDialogModel m, SelectionListener l) {
    super(new RevisionsTableModel(m));
    setDefaultRenderer(Object.class, new RevisionsTableCellRenderer());

    TableColumnModel cm = getColumnModel();

    cm.getColumn(0).setMinWidth(150);
    cm.getColumn(0).setMaxWidth(150);

    cm.getColumn(0).setResizable(false);
    cm.getColumn(1).setResizable(false);

    setCellSelectionEnabled(true);

    addSelectionListener(l);
  }

  private void addSelectionListener(SelectionListener listener) {
    ListSelectionModel sm = getSelectionModel();
    ListSelectionModel csm = getColumnModel().getSelectionModel();

    MyListSelectionListener l = new MyListSelectionListener(listener);
    sm.addListSelectionListener(l);
    csm.addListSelectionListener(l);

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

  public interface SelectionListener {
    void changesSelected(int first, int last);

    void revisionsSelected(int first, int last);
  }

  private class MyListSelectionListener implements ListSelectionListener {
    private int mySelectedRow1 = 0;
    private int mySelectedRow2 = 0;
    private int mySelectedColumn1 = 1;
    private int mySelectedColumn2 = 1;
    private SelectionListener mySelectionListener;

    public MyListSelectionListener(SelectionListener l) {
      mySelectionListener = l;
    }

    public void valueChanged(ListSelectionEvent e) {
      ListSelectionModel sm = getSelectionModel();
      ListSelectionModel csm = getColumnModel().getSelectionModel();

      if (!isValidSelection()) {
        sm.setSelectionInterval(mySelectedRow1, mySelectedRow2);
        csm.setSelectionInterval(mySelectedColumn1, mySelectedColumn2);
      }
      else {
        if (e.getValueIsAdjusting()) return;

        mySelectedRow1 = sm.getMinSelectionIndex();
        mySelectedRow2 = sm.getMaxSelectionIndex();
        mySelectedColumn1 = csm.getMinSelectionIndex();
        mySelectedColumn2 = csm.getMaxSelectionIndex();

        if (mySelectedColumn1 == 2) {
          mySelectionListener.changesSelected(mySelectedRow1, mySelectedRow2);
        }
        else {
          mySelectionListener.revisionsSelected(mySelectedRow1, mySelectedRow2);
        }
      }
    }

    private boolean isValidSelection() {
      if (getSelectedColumnCount() > 1) return false;
      if (getSelectedColumn() < 2) return true;
      return !isRowSelected(getRowCount() - 1);
    }
  }
}
