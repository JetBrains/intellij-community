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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;

public class ListScrollingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.ListScrollingUtil");
  @NonNls
  protected static final String SCROLLUP_ACTION_ID = "scrollUp";
  @NonNls
  protected static final String SCROLLDOWN_ACTION_ID = "scrollDown";
  @NonNls
  protected static final String SELECT_PREVIOUS_ROW_ACTION_ID = "selectPreviousRow";
  @NonNls
  protected static final String SELECT_NEXT_ROW_ACTION_ID = "selectNextRow";
  @NonNls
  protected static final String SELECT_LAST_ROW_ACTION_ID = "selectLastRow";
  @NonNls
  protected static final String SELECT_FIRST_ROW_ACTION_ID = "selectFirstRow";

  public static final int ROW_PADDING = 2;

  public static void selectItem(JList list, int index) {
    LOG.assertTrue(index >= 0);
    LOG.assertTrue(index < list.getModel().getSize());
    ensureIndexIsVisible(list, index, 0);
    list.setSelectedIndex(index);
  }

  public static void ensureSelectionExists(JList list) {
    int size = list.getModel().getSize();
    if (size == 0) {
      list.clearSelection();
      return;
    }
    int selectedIndex = list.getSelectedIndex();
    if (selectedIndex < 0 || selectedIndex >= size) { // fit index to [0, size-1] range
      selectedIndex = 0;
    }
    selectItem(list, selectedIndex);
  }

  public static boolean selectItem(JList list, @NotNull Object item) {
    ListModel model = list.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Object anItem = model.getElementAt(i);
      if (item.equals(anItem)) {
        selectItem(list, i);
        return true;
      }
    }
    return false;
  }

  public static void movePageUp(JList list) {
    int visible = getVisibleRowCount(list);
    ListSelectionModel selectionModel = list.getSelectionModel();
    if (visible <= 0) {
      moveHome(list);
      return;
    }
    int size = list.getModel().getSize();
    int decrement = visible - 1;
    int index = Math.max(list.getSelectedIndex() - decrement, 0);
    int top = list.getFirstVisibleIndex() - decrement;
    if (top < 0) {
      top = 0;
    }
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }
    //list.clearSelection();
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds == null) {
      moveHome(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);
    selectionModel.setSelectionInterval(index,index);
    list.ensureIndexIsVisible(index);
  }

  public static void movePageDown(JList list) {
    int visible = getVisibleRowCount(list);
    if (visible <= 0) {
      moveEnd(list);
      return;
    }
    int size = list.getModel().getSize();
    int increment = visible - 1;
    int index = Math.min(list.getSelectedIndex() + increment, size - 1);
    int top = list.getFirstVisibleIndex() + increment;
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }
    //list.clearSelection();
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds == null) {
      moveEnd(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void moveHome(JList list) {
    list.setSelectedIndex(0);
    list.ensureIndexIsVisible(0);
  }

  public static void moveEnd(JList list) {
    int index = list.getModel().getSize() - 1;
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void ensureIndexIsVisible(JList list, int index, int moveDirection) {
    int visible = getVisibleRowCount(list);
    int size = list.getModel().getSize();
    int top;
    int bottom;
    if (moveDirection == 0) {
      top = index - (visible - 1) / ROW_PADDING;
      bottom = top + visible - 1;
    }
    else if (moveDirection < 0) {
      top = index - ROW_PADDING;
      bottom = index;
    }
    else {
      top = index;
      bottom = index + ROW_PADDING;
    }
    if (top < 0) {
      top = 0;
    }
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds != null) {
      cellBounds.x = 0;
      list.scrollRectToVisible(cellBounds);
    }
  }

  private static int getVisibleRowCount(JList list) {
    return list.getLastVisibleIndex() - list.getFirstVisibleIndex() + 1;
  }

  public static void moveDown(JList list, final int modifiers) {
    int size = list.getModel().getSize();
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

  public static void moveUp(JList list, int modifiers) {
    int size = list.getModel().getSize();
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

  public static void installActions(final JList list) {
    ActionMap actionMap = list.getActionMap();
    actionMap.put(SCROLLUP_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageUp(list);
      }
    });
    actionMap.put(SCROLLDOWN_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        movePageDown(list);
      }
    });
    actionMap.put(SELECT_PREVIOUS_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveUp(list, e.getModifiers());
      }
    });
    actionMap.put(SELECT_NEXT_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveDown(list, e.getModifiers());
      }
    });
    actionMap.put(SELECT_LAST_ROW_ACTION_ID, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        moveEnd(list);
      }
    });
    actionMap.put(SELECT_FIRST_ROW_ACTION_ID, new AbstractAction() {
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


    InputMap map = list.getInputMap(JComponent.WHEN_FOCUSED);
    UIUtil.maybeInstall(map, SCROLLUP_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
    UIUtil.maybeInstall(map, SCROLLDOWN_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_PREVIOUS_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    UIUtil.maybeInstall(map, SELECT_NEXT_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_FIRST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
    UIUtil.maybeInstall(map, SELECT_LAST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));

  }
}
