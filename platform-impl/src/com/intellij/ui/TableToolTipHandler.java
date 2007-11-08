package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

public class TableToolTipHandler extends AbstractToolTipHandler<TableCellKey, JTable> {
  protected TableToolTipHandler(JTable table) {
    super(table);
  }

  public static void install(JTable table) {
    new TableToolTipHandler(table);
  }

  public Rectangle getCellBounds(TableCellKey tableCellKey, Component rendererComponent) {
    Rectangle cellRect = getCellRect(tableCellKey);
    cellRect.width = rendererComponent.getPreferredSize().width;
    return cellRect;
  }

  private Rectangle getCellRect(TableCellKey tableCellKey) {
    return myComponent.getCellRect(tableCellKey.myRowIndex, tableCellKey.myColumnIndex, false);
  }

  public Component getRendererComponent(TableCellKey key) {
    int modelColumnIndex = myComponent.convertColumnIndexToModel(key.myColumnIndex);
    return myComponent.getCellRenderer(key.myRowIndex, key.myColumnIndex).
      getTableCellRendererComponent(myComponent,
                                    myComponent.getModel().getValueAt(key.myRowIndex, modelColumnIndex),
                                    myComponent.getSelectionModel().isSelectedIndex(key.myRowIndex),
                                    myComponent.hasFocus(),
                                    key.myRowIndex,key.myColumnIndex
      );
  }

  public Rectangle getVisibleRect(TableCellKey key) {
    Rectangle columnVisibleRect = myComponent.getVisibleRect();
    Rectangle cellRect = getCellRect(key);
    int visibleRight = Math.min(columnVisibleRect.x + columnVisibleRect.width, cellRect.x + cellRect.width);
    columnVisibleRect.x = Math.min(columnVisibleRect.x, cellRect.x);
    columnVisibleRect.width = Math.max(0, visibleRight - columnVisibleRect.x);
    return columnVisibleRect;
  }

  //private Rectangle getColumnRectangle(int columnIndex) {
  //  TableColumnModel cm = getColumnModel();
  //  if (getComponentOrientation().isLeftToRight()) {
  //    for (int i = 0; i < column; i++) {
  //      r.x += cm.getColumn(i).getWidth();
  //    }
  //  }
  //  else {
  //    for (int i = cm.getColumnCount() - 1; i > column; i--) {
  //      r.x += cm.getColumn(i).getWidth();
  //    }
  //  }
  //  r.width = cm.getColumn(column).getWidth();
  //}

  public TableCellKey getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.rowAtPoint(point);
    if (rowIndex == -1) {
      return null;
    }

    int columnIndex = myComponent.columnAtPoint(point); // column index in visible coordinates
    if (columnIndex == -1) {
      return null;
    }

    return new TableCellKey(rowIndex, columnIndex);
  }
}
