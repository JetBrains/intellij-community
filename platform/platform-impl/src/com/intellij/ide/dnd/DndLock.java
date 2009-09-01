package com.intellij.ide.dnd;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class DndLock extends JFrame {
  private final static Object[][] TABLE_DATA = new Object[][] {
    new Object[] {new Integer(1), "Red"},
    new Object[] {new Integer(2), "Green"},
    new Object[] {new Integer(2), "Blue"}
  };
  private final static String[] TABLE_COLUMNS = new String[] {
    "Index", "Color"
  };

  public DndLock() {
    super("DndLock");
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JTable table = new JTable(TABLE_DATA, TABLE_COLUMNS);
    table.setDragEnabled(true);
    getContentPane().add(table, BorderLayout.CENTER);
    setSize(400, 400);
  }

  public static void main(String[] args) {
    new DndLock().setVisible(true);
  }
}
