/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.util.Pair;

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

  private static Rectangle getCellBounds(JTable table, int top, int bottom) {
    Rectangle cellBounds = table.getCellRect(top, 0, true).union(table.getCellRect(bottom,0,true));
    return cellBounds;
  }

  private static int getVisibleRowCount(JTable list) {
    Rectangle visibleRect = list.getVisibleRect();
    return getTrailingRow(list, visibleRect) - getLeadingRow(list, visibleRect) + 1;
  }

  public static Pair<Integer, Integer> getVisibleRows(JTable list) {
    Rectangle visibleRect = list.getVisibleRect();
    return new Pair<Integer, Integer>(getLeadingRow(list, visibleRect) + 1, getTrailingRow(list, visibleRect));
  }

  private static int getLeadingRow(JTable table,Rectangle visibleRect) {
      Point leadingPoint;

      if (table.getComponentOrientation().isLeftToRight()) {
          leadingPoint = new Point(visibleRect.x, visibleRect.y);
      }
      else {
          leadingPoint = new Point(visibleRect.x + visibleRect.width,
                                   visibleRect.y);
      }
      return table.rowAtPoint(leadingPoint);
  }

  private static int getTrailingRow(JTable table,Rectangle visibleRect) {
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


  public static void moveDown(JTable list, final int modifiers) {
    int size = list.getModel().getRowCount();
    if (size == 0) {
      return;
    }
    final ListSelectionModel selectionModel = list.getSelectionModel();
    int index = selectionModel.getLeadSelectionIndex();
    boolean cycleScrolling = UISettings.getInstance().CYCLE_SCROLLING;
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
    ensureIndexIsVisible(list, indexToSelect, +1);
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

  public static void moveUp(JTable list, int modifiers) {
    int size = list.getModel().getRowCount();
    final ListSelectionModel selectionModel = list.getSelectionModel();
    int index = selectionModel.getMinSelectionIndex();
    boolean cycleScrolling = UISettings.getInstance().CYCLE_SCROLLING;
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
    ensureIndexIsVisible(list, indexToSelect, -1);
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

  public static void moveHome(JTable list) {
    list.getSelectionModel().setSelectionInterval(0,0);
    ensureIndexIsVisible(list, 0,0);
  }

  public static void moveEnd(JTable list) {
    int index = list.getModel().getRowCount() - 1;
    list.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(list, index, 0);
  }

  public static void movePageUp(JTable list) {
    int visible = getVisibleRowCount(list);
    if (visible <= 0) {
      moveHome(list);
      return;
    }
    int size = list.getModel().getRowCount();
    int decrement = visible - 1;
    ListSelectionModel selectionModel = list.getSelectionModel();
    int index = Math.max(selectionModel.getMinSelectionIndex() - decrement, 0);
    int visibleIndex = getLeadingRow(list, list.getVisibleRect());
    int top = visibleIndex - decrement;
    if (top < 0) {
      top = 0;
    }
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }

    Rectangle cellBounds = getCellBounds(list, top, bottom);
    if (cellBounds == null) {
      moveHome(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);

    list.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(list, index, 0);
  }

  public static void movePageDown(JTable list) {
    int visible = getVisibleRowCount(list);
    if (visible <= 0) {
      moveEnd(list);
      return;
    }
    ListSelectionModel selectionModel = list.getSelectionModel();
    int size = list.getModel().getRowCount();
    int increment = visible - 1;
    int index = Math.min(selectionModel.getMinSelectionIndex() + increment, size - 1);
    int fisrtVisibleRow = getLeadingRow(list, list.getVisibleRect());
    int top = fisrtVisibleRow + increment;
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = getCellBounds(list, top, bottom);
    if (cellBounds == null) {
      moveEnd(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);
    list.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(list, index, 0);
  }

  public static void installActions(final JTable list) {
    ActionMap actionMap = list.getActionMap();
    actionMap.put(ListScrollingUtil.SCROLLUP_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageUp(list);
      }
    });
    actionMap.put(ListScrollingUtil.SCROLLDOWN_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageDown(list);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_PREVIOUS_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveUp(list, e.getModifiers());
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_NEXT_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveDown(list, e.getModifiers());
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_LAST_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveEnd(list);
      }
    });
    actionMap.put(ListScrollingUtil.SELECT_FIRST_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveHome(list);
      }
    });
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveHome(list);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), list);
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        moveEnd(list);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), list);
  }

}
