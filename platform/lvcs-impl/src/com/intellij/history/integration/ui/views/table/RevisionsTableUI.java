package com.intellij.history.integration.ui.views.table;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.TableUI;
import javax.swing.plaf.basic.BasicTableUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class RevisionsTableUI extends BasicTableUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.localVcs.ui.RevisionListTableUI2");
  private final TableUI myBaseUI;
  @NonNls
  protected static final String SELECTION_MODEL_PROPERTY_NAME = "selectionModel";

  public RevisionsTableUI(TableUI ui) {
    myBaseUI = ui;
  }

  public void installUI(JComponent c) {
    myBaseUI.installUI(c);

    table = (JTable)c;
    rendererPane = new CellRendererPane();
    table.add(rendererPane);

    table.addPropertyChangeListener(SELECTION_MODEL_PROPERTY_NAME, new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        removeSelectionListener((ListSelectionModel)evt.getOldValue());
      }
    });
  }

  private void removeSelectionListener(ListSelectionModel listSelectionModel) {
    ListSelectionListener myRowSelectionListener = new ListSelectionListener() {
      boolean myDuringChange = false;

      public void valueChanged(ListSelectionEvent e) {
        if (myDuringChange) return;
        int focusedColumn = table.getColumnModel().getSelectionModel().getAnchorSelectionIndex();
        if (focusedColumn != table.getColumnCount() - 1) return;
        myDuringChange = true;
      }
    };
    listSelectionModel.addListSelectionListener(myRowSelectionListener);
  }

  public void uninstallUI(JComponent c) {
    myBaseUI.uninstallUI(c);
    table.remove(rendererPane);
    rendererPane = null;
    table = null;
  }

  public void paint(Graphics g, JComponent c) {
    LOG.assertTrue(table != null && rendererPane != null, "Disposed");
    JTable table = (JTable)c;
    //g.setColor(Color.GREEN);
    //g.fillRect(0, 0, table.getWidth(), table.getHeight());
    if (table.getRowCount() == 0 || table.getColumnCount() == 0) return;
    VisibleCells visibleCells = visibleCells(table, g.getClipBounds());
    if (visibleCells.hasShiftedColumn()) drawShiftedColumns(g, visibleCells);
    Shape prevClip = visibleCells.clipShiftedCells(g);
    myBaseUI.paint(g, c);
    g.setClip(prevClip);
  }

  private void drawShiftedColumns(Graphics g, VisibleCells visibleCells) {
    int column = visibleCells.getShiftedColumn();
    Rectangle firstCell = visibleCells.getTopShiftedCell();
    int x = firstCell.x;
    int columnWidth = firstCell.width;
    int lineY = firstCell.y - 1;

    for (int row = visibleCells.getFirstRow(); row <= visibleCells.getLastRow(); row++) {
      int height = table.getRowHeight(row);
      int cellY = lineY + height / 2;
      Component component = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
      rendererPane.paintComponent(g, component, table, x, cellY, columnWidth, height + 1, true);
      lineY += height;
    }
  }

  private VisibleCells visibleCells(JTable table, Rectangle visibleArea) {
    int maxRowIndex = table.getRowCount() - 2;
    int firstRow = correctRange(visibleArea.y / table.getRowHeight() - 1, 0, maxRowIndex);
    Point corner = new Point(visibleArea.x + visibleArea.width, visibleArea.y + visibleArea.height);
    int lastRow = correctRange(corner.y / table.getRowHeight(), 0, maxRowIndex);
    int firstColumn = table.columnAtPoint(visibleArea.getLocation());
    int lastColumn = table.columnAtPoint(corner);
    if (lastColumn == -1) lastColumn = table.getColumnCount() - 1;
    return new VisibleCells(table, firstRow, firstColumn, lastRow, lastColumn);
  }

  private int correctRange(int value, int min, int max) {
    if (value < min) value = min;
    if (value > max) value = max;
    return value;
  }

  private static class VisibleCells {
    private final int myFirstRow;
    private final int myFirstColumn;
    private final int myLastRow;
    private final int myLastColumn;
    private final JTable myTable;

    public VisibleCells(JTable table, int firstRow, int firstColumn, int lastRow, int lastColumn) {
      myTable = table;
      myFirstRow = firstRow;
      myFirstColumn = firstColumn;
      myLastRow = lastRow;
      myLastColumn = lastColumn;
    }

    public Rectangle leftTopCell(JTable table) {
      return table.getCellRect(myFirstRow, myFirstColumn, true);
    }

    public int getFirstRow() {
      return myFirstRow;
    }

    public int getLastRow() {
      return myLastRow;
    }

    public int getEndX(JTable table) {
      Rectangle rightBottom = table.getCellRect(myLastRow, myLastColumn, true);
      return rightBottom.x + rightBottom.width;
    }

    public Shape clipShiftedCells(Graphics g) {
      int lastColumn = hasShiftedColumn() ? myLastColumn - 1 : myLastColumn;
      Rectangle baseCellsRect = getAreaRect(myFirstRow, myFirstColumn, myLastRow, lastColumn);
      Shape prevClip = g.getClip();
      baseCellsRect = g.getClipBounds().intersection(baseCellsRect);
      g.setClip(baseCellsRect.x, baseCellsRect.y, baseCellsRect.width, baseCellsRect.height);
      return prevClip;
    }

    public boolean hasShiftedColumn() {
      return myTable.getColumnCount() - 1 == myLastColumn && myLastRow >= 0 && myFirstRow >= 0;
    }

    private Rectangle getAreaRect(int firstRow, int firstColumn, int lastRow, int lastColumn) {
      Point leftTop = myTable.getCellRect(firstRow, firstColumn, true).getLocation();
      Rectangle rightBottomCell = myTable.getCellRect(lastRow, lastColumn, true);
      Point rightBottom = new Point(rightBottomCell.x + rightBottomCell.width, rightBottomCell.y + rightBottomCell.width);
      return new Rectangle(leftTop.x, leftTop.y, rightBottom.x - leftTop.x, rightBottom.y - leftTop.y);
    }

    public Rectangle getTopShiftedCell() {
      return myTable.getCellRect(myFirstRow, myLastColumn, true);
    }

    public Color getGrigColor() {
      return myTable.getGridColor();
    }

    public int getShiftedColumn() {
      return myTable.getColumnCount() - 1;
    }
  }
}