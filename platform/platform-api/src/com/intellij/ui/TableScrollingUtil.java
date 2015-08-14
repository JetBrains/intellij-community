/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.util.Couple;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author cdr
 */
public class TableScrollingUtil {
  public static void ensureIndexIsVisible(JTable table, int index, int moveDirection) {
    int visible = getVisibleRowCount(table);
    int size = table.getModel().getRowCount();
    int top;
    int bottom;
    if (moveDirection == 0) {
      top = index - (visible - 1) / ListScrollingUtil.ROW_PADDING;
      bottom = top + visible - 1;
    }
    else if (moveDirection < 0) {
      top = index - ListScrollingUtil.ROW_PADDING;
      bottom = index;
    }
    else {
      top = index;
      bottom = index + ListScrollingUtil.ROW_PADDING;
    }
    if (top < 0) {
      top = 0;
    }
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = getCellBounds(table, top, bottom);
    if (cellBounds != null) {
      cellBounds.x = 0;
      table.scrollRectToVisible(cellBounds);
    }
  }

  public static void ensureSelectionExists(@NotNull JTable table) {
    int size = table.getModel().getRowCount();
    if (size == 0) {
      table.clearSelection();
      return;
    }
    int selectedIndex = table.getSelectedRow();
    boolean reselect = false;
    if (selectedIndex < 0 || selectedIndex >= size) { // fit index to [0, size-1] range
      selectedIndex = 0;
      reselect = true;
    }
    ensureIndexIsVisible(table, selectedIndex, 0);
    if (reselect) {
      table.getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
    }
  }

  private static Rectangle getCellBounds(JTable table, int top, int bottom) {
    return table.getCellRect(top, 0, true).union(table.getCellRect(bottom,0,true));
  }

  private static int getVisibleRowCount(JTable table) {
    Rectangle visibleRect = table.getVisibleRect();
    return getTrailingRow(table, visibleRect) - getLeadingRow(table, visibleRect) + 1;
  }

  public static Couple<Integer> getVisibleRows(JTable table) {
    Rectangle visibleRect = table.getVisibleRect();
    return Couple.of(getLeadingRow(table, visibleRect) + 1, getTrailingRow(table, visibleRect));
  }

  private static int getLeadingRow(JTable table, Rectangle visibleRect) {
    return table.rowAtPoint(getLeadingPoint(table, visibleRect));
  }

  private static Point getLeadingPoint(JTable table, Rectangle visibleRect) {
    if (table.getComponentOrientation().isLeftToRight()) {
        return new Point(visibleRect.x, visibleRect.y);
    }
    else {
        return new Point(visibleRect.x + visibleRect.width,
                                 visibleRect.y);
    }
  }

  public static int getReadableRow(JTable table, int maximumHiddenPart) {
    Rectangle visibleRect = table.getVisibleRect();
    Point leadingPoint = getLeadingPoint(table, visibleRect);
    int row = table.rowAtPoint(leadingPoint);
    int column = table.columnAtPoint(leadingPoint);
    if (leadingPoint.y - table.getCellRect(row, column, true).getY() <= maximumHiddenPart) {
      return row;
    } else {
      return Math.min(row + 1, table.getRowCount() - 1); // just in case
    }

  }

  private static int getTrailingRow(JTable table, Rectangle visibleRect) {
      Point trailingPoint;

      if (table.getComponentOrientation().isLeftToRight()) {
          trailingPoint = new Point(visibleRect.x,
                                    visibleRect.y + visibleRect.height - 1);
      }
      else {
          trailingPoint = new Point(visibleRect.x + visibleRect.width,
                                    visibleRect.y + visibleRect.height - 1);
      }
      return table.rowAtPoint(trailingPoint);
  }


  public static void moveDown(JTable table, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    int size = table.getModel().getRowCount();
    if (size == 0) {
      return;
    }
    final ListSelectionModel selectionModel = table.getSelectionModel();
    int index = selectionModel.getLeadSelectionIndex();
    final int indexToSelect;
    if (index < size - 1) {
      indexToSelect = index + 1;
    }
    else if (cycleScrolling && index == size - 1) {
      indexToSelect = 0;
    }
    else {
      return;
    }
    ensureIndexIsVisible(table, indexToSelect, +1);
    if (selectionModel.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) {
      selectionModel.setSelectionInterval(indexToSelect,indexToSelect);
    }
    else {
      if ((modifiers & InputEvent.SHIFT_DOWN_MASK) == 0) {
        selectionModel.removeSelectionInterval(selectionModel.getMinSelectionIndex(), selectionModel.getMaxSelectionIndex());
      }
      selectionModel.addSelectionInterval(indexToSelect, indexToSelect);
    }
  }

  public static void moveUp(JTable table, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    int size = table.getModel().getRowCount();
    final ListSelectionModel selectionModel = table.getSelectionModel();
    int index = selectionModel.getMinSelectionIndex();
    int indexToSelect;
    if (index > 0) {
      indexToSelect = index - 1;
    }
    else if (cycleScrolling && index == 0) {
      indexToSelect = size - 1;
    }
    else {
      return;
    }
    ensureIndexIsVisible(table, indexToSelect, -1);
    if (selectionModel.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) {
      selectionModel.setSelectionInterval(indexToSelect, indexToSelect);
    }
    else {
      if ((modifiers & InputEvent.SHIFT_DOWN_MASK) == 0) {
        selectionModel.removeSelectionInterval(selectionModel.getMinSelectionIndex(), selectionModel.getMaxSelectionIndex());
      }
      selectionModel.addSelectionInterval(indexToSelect, indexToSelect);
    }
  }

  public static void moveHome(JTable table) {
    table.getSelectionModel().setSelectionInterval(0,0);
    ensureIndexIsVisible(table, 0,0);
  }

  public static void moveEnd(JTable table) {
    int index = table.getModel().getRowCount() - 1;
    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void movePageUp(JTable table) {
    int visible = getVisibleRowCount(table);
    if (visible <= 0) {
      moveHome(table);
      return;
    }
    int size = table.getModel().getRowCount();
    int decrement = visible - 1;
    ListSelectionModel selectionModel = table.getSelectionModel();
    int index = Math.max(selectionModel.getMinSelectionIndex() - decrement, 0);
    int visibleIndex = getLeadingRow(table, table.getVisibleRect());
    int top = visibleIndex - decrement;
    if (top < 0) {
      top = 0;
    }
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }

    Rectangle cellBounds = getCellBounds(table, top, bottom);
    if (cellBounds == null) {
      moveHome(table);
      return;
    }
    table.scrollRectToVisible(cellBounds);

    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void movePageDown(JTable table) {
    int visible = getVisibleRowCount(table);
    if (visible <= 0) {
      moveEnd(table);
      return;
    }
    ListSelectionModel selectionModel = table.getSelectionModel();
    int size = table.getModel().getRowCount();
    int increment = visible - 1;
    int index = Math.min(selectionModel.getMinSelectionIndex() + increment, size - 1);
    int fisrtVisibleRow = getLeadingRow(table, table.getVisibleRect());
    int top = fisrtVisibleRow + increment;
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = getCellBounds(table, top, bottom);
    if (cellBounds == null) {
      moveEnd(table);
      return;
    }
    table.scrollRectToVisible(cellBounds);
    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void installActions(final JTable table) {
    installActions(table, UISettings.getInstance().CYCLE_SCROLLING);
  }

  public static void installActions(final JTable table, final boolean cycleScrolling) {
    ActionMap actionMap = table.getActionMap();
    actionMap.put(ListScrollingUtil.SCROLLUP_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageUp(table);
      }
    });
    actionMap.put(ListScrollingUtil.SCROLLDOWN_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageDown(table);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_PREVIOUS_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveUp(table, e.getModifiers(), cycleScrolling);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_NEXT_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveDown(table, e.getModifiers(), cycleScrolling);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_LAST_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveEnd(table);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_FIRST_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveHome(table);
      }
    });

    ListScrollingUtil.maybeInstallDefaultShortcuts(table);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveHome(table);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveEnd(table);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), table);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveHome(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveHome(), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveEnd(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveEnd(), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveDown(table, e.getModifiers(), cycleScrolling);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveDown(), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveUp(table, e.getModifiers(), cycleScrolling);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveUp(), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        movePageUp(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMovePageUp(), table);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        movePageDown(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMovePageDown(), table);
  }

}
