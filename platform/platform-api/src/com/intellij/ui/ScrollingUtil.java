/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * A helper class for registering Keymap aware navigation actions for lists and trees
 *
 * @author Konstantin Bulenkov
 * @since 15.0
 */
public class ScrollingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.ScrollingUtil");
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
  @NonNls
  protected  static final String MOVE_HOME_ID = "MOVE_HOME";
  @NonNls
  protected  static final String MOVE_END_ID = "MOVE_END";

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
    _ensureIndexIsVisible(list, index, moveDirection, list.getModel().getSize());
  }

  public static void ensureIndexIsVisible(JTable table, int index, int moveDirection) {
    _ensureIndexIsVisible(table, index, moveDirection, table.getRowCount());
  }

  private static void _ensureIndexIsVisible(JComponent c, int index, int moveDirection, int size) {
    int visible = visibleRowCount(c);
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
    _ensureRangeIsVisible(c, top, bottom);
  }

  public static void ensureRangeIsVisible(JList list, int top, int bottom) {
    int size = list.getModel().getSize();
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
  private static void _ensureRangeIsVisible(JComponent c, int top, int bottom) {
    if (c instanceof JList) {
      JList list = ((JList)c);
      int size = list.getModel().getSize();
      if (top < 0) {
        top = 0;
      }
      if (bottom >= size) {
        bottom = size - 1;
      }
      Rectangle cellBounds = list.getCellBounds(top, bottom);
      if (cellBounds != null) {
        cellBounds.x = 0;
        c.scrollRectToVisible(cellBounds);
      }
    }
    else if (c instanceof JTable) {
      JTable table = (JTable)c;
      Rectangle cellBounds = getCellBounds(table, top, bottom);
      cellBounds.x = 0;
      table.scrollRectToVisible(cellBounds);
    }
  }

  public static boolean isIndexFullyVisible(JList list, int index) {
    int first = list.getFirstVisibleIndex();
    int last = list.getLastVisibleIndex();

    if (index < 0 || first < 0 || last < 0 || index < first || index > last) {
      return false;
    }
    if (index > first && index < last) {
      return true;
    }

    return list.getVisibleRect().contains(list.getCellBounds(index, index));
  }

  private static int getVisibleRowCount(JList list) {
    return list.getLastVisibleIndex() - list.getFirstVisibleIndex() + 1;
  }

  public static void moveDown(JList list, @JdkConstants.InputEventMask final int modifiers) {
    _moveDown(list, list.getSelectionModel(), modifiers, list.getModel().getSize(), UISettings.getInstance().getCycleScrolling());
  }

  private static void selectOrAddSelection(ListSelectionModel selectionModel,
                                           int indexToSelect,
                                           @JdkConstants.InputEventMask int modifiers) {
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

  public static void installActions(final JList list) {
    installActions(list, null);
  }

  public static void installActions(final JList list, @Nullable JComponent focusParent) {
    ActionMap actionMap = list.getActionMap();
    actionMap.put(SCROLLUP_ACTION_ID, new MoveAction(SCROLLUP_ACTION_ID, list));
    actionMap.put(SCROLLDOWN_ACTION_ID, new MoveAction(SCROLLDOWN_ACTION_ID, list));
    actionMap.put(SELECT_PREVIOUS_ROW_ACTION_ID, new MoveAction(SELECT_PREVIOUS_ROW_ACTION_ID, list));
    actionMap.put(SELECT_NEXT_ROW_ACTION_ID, new MoveAction(SELECT_NEXT_ROW_ACTION_ID, list));
    actionMap.put(SELECT_LAST_ROW_ACTION_ID, new MoveAction(SELECT_LAST_ROW_ACTION_ID, list));
    actionMap.put(SELECT_FIRST_ROW_ACTION_ID, new MoveAction(SELECT_FIRST_ROW_ACTION_ID, list));
    actionMap.put(MOVE_HOME_ID, new MoveAction(MOVE_HOME_ID, list));
    actionMap.put(MOVE_END_ID, new MoveAction(MOVE_END_ID, list));

    maybeInstallDefaultShortcuts(list);

    installMoveUpAction(list, focusParent);
    installMoveDownAction(list, focusParent);
    installMovePageUpAction(list, focusParent);
    installMovePageDownAction(list, focusParent);
    if (!(focusParent instanceof JTextComponent)) {
      installMoveHomeAction(list, focusParent);
      installMoveEndAction(list, focusParent);
    }
  }

  public static void installMoveEndAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMoveEnd(), focusParent == null ? list : focusParent){
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveEnd(list);
      }
    };
  }

  public static void installMoveHomeAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMoveHome(), focusParent == null ? list : focusParent){
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveHome(list);
      }
    };
  }

  public static void installMovePageDownAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMovePageDown(), focusParent == null ? list : focusParent){
      @Override
      public void actionPerformed(AnActionEvent e) {
        movePageDown(list);
      }
    };
  }

  public static void installMovePageUpAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMovePageUp(), focusParent == null ? list : focusParent){
      @Override
      public void actionPerformed(AnActionEvent e) {
        movePageUp(list);
      }
    };
  }

  public static void installMoveDownAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMoveDown(), focusParent == null ? list : focusParent){
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveDown(list, 0);
      }
    };
  }

  public static void installMoveUpAction(final JList list, @Nullable JComponent focusParent) {
    new ListScrollAction(CommonShortcuts.getMoveUp(), focusParent == null ? list : focusParent) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveUp(list, 0);
      }
    };
  }

  static void maybeInstallDefaultShortcuts(JComponent component) {
    InputMap map = component.getInputMap(JComponent.WHEN_FOCUSED);
    UIUtil.maybeInstall(map, SCROLLUP_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
    UIUtil.maybeInstall(map, SCROLLDOWN_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_PREVIOUS_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    UIUtil.maybeInstall(map, SELECT_NEXT_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_FIRST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
    UIUtil.maybeInstall(map, SELECT_LAST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));
    UIUtil.maybeInstall(map, MOVE_HOME_ID, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    UIUtil.maybeInstall(map, MOVE_END_ID, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
  }

  public interface ScrollingAction extends DumbAware {

  }

  public static abstract class ListScrollAction extends MyScrollingAction {
    protected ListScrollAction(final ShortcutSet shortcutSet, final JComponent component) {
      super(component);
      registerCustomShortcutSet(shortcutSet, component);
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

  @NotNull
  private static Rectangle getCellBounds(JTable table, int top, int bottom) {
    return table.getCellRect(top, 0, true).union(table.getCellRect(bottom,0,true));
  }

  private static int visibleRowCount(JComponent c) {
    if (c instanceof JList) return getVisibleRowCount((JList)c);
    if (c instanceof JTable) return getVisibleRowCount((JTable)c);
    return -1;
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

  public static boolean isVisible(JTable table, int row) {
    Rectangle visibleRect = table.getVisibleRect();
    int start = getLeadingRow(table, visibleRect);
    int end = getTrailingRow(table, visibleRect);
    return row >= start && row <= end;
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
    _moveDown(table, table.getSelectionModel(), modifiers, table.getRowCount(), cycleScrolling);
  }

  public static void moveUp(JList list, @JdkConstants.InputEventMask int modifiers) {
    _moveUp(list, list.getSelectionModel(), list.getModel().getSize(), modifiers, UISettings.getInstance().getCycleScrolling());
  }

  public static void moveUp(JTable table, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _moveUp(table, table.getSelectionModel(), table.getModel().getRowCount(), modifiers, cycleScrolling);
  }

  private static void _moveDown(JComponent c, ListSelectionModel selectionModel, @JdkConstants.InputEventMask final int modifiers, int size, boolean cycleScrolling) {
    _move(c, selectionModel, modifiers, size, cycleScrolling, +1);
  }

  private static void _move(JComponent c, ListSelectionModel selectionModel, @JdkConstants.InputEventMask final int modifiers, int size, boolean cycleScrolling, int direction) {
    if (size == 0) return;
    int index = selectionModel.getLeadSelectionIndex();
    int indexToSelect = index + direction;
    if (indexToSelect < 0 || indexToSelect >= size) {
      if (cycleScrolling) {
        indexToSelect = indexToSelect < 0 ? size - 1 : 0;
      } else {
        return;
      }
    }
    _ensureIndexIsVisible(c, indexToSelect, -1, size);
    selectOrAddSelection(selectionModel, indexToSelect, modifiers);
  }

  private static void _moveUp(JComponent c, ListSelectionModel selectionModel, int size, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _move(c, selectionModel, modifiers, size, cycleScrolling, -1);
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
    int firstVisibleRow = getLeadingRow(table, table.getVisibleRect());
    int top = firstVisibleRow + increment;
    int bottom = top + visible - 1;
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = getCellBounds(table, top, bottom);
    table.scrollRectToVisible(cellBounds);
    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void installActions(final JTable table) {
    installActions(table, UISettings.getInstance().getCycleScrolling());
  }

  private abstract static class MyScrollingAction extends DumbAwareAction implements ScrollingAction {
    private final JComponent myComponent;

    MyScrollingAction(JComponent component) {
      myComponent = component;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(SpeedSearchSupply.getSupply(myComponent) == null && !isEmpty(myComponent));
    }
  }

  public static boolean isEmpty(JComponent component) {
    if (component instanceof JTable) return ((JTable)component).getRowCount() < 1;
    if (component instanceof JList) return ((JList)component).getModel().getSize() <1;
    return false;
  }

  public static void installActions(final JTable table, final boolean cycleScrolling) {
    installActions(table, cycleScrolling, null);
  }

  public static void installActions(final JTable table, final boolean cycleScrolling, JComponent focusParent) {
    ActionMap actionMap = table.getActionMap();
    actionMap.put(SCROLLUP_ACTION_ID, new MoveAction(SCROLLUP_ACTION_ID, table, cycleScrolling));
    actionMap.put(SCROLLDOWN_ACTION_ID, new MoveAction(SCROLLDOWN_ACTION_ID, table, cycleScrolling));
    actionMap.put(SELECT_PREVIOUS_ROW_ACTION_ID, new MoveAction(SELECT_PREVIOUS_ROW_ACTION_ID, table, cycleScrolling));
    actionMap.put(SELECT_NEXT_ROW_ACTION_ID, new MoveAction(SELECT_NEXT_ROW_ACTION_ID, table, cycleScrolling));
    actionMap.put(SELECT_LAST_ROW_ACTION_ID, new MoveAction(SELECT_LAST_ROW_ACTION_ID, table, cycleScrolling));
    actionMap.put(SELECT_FIRST_ROW_ACTION_ID, new MoveAction(SELECT_FIRST_ROW_ACTION_ID, table, cycleScrolling));

    maybeInstallDefaultShortcuts(table);
    JComponent target = focusParent == null ? table : focusParent;

    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveHome(table);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), table);
    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveEnd(table);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), table);
    if (!(focusParent instanceof JTextComponent)) {
      new MyScrollingAction(table) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          moveHome(table);
        }
      }.registerCustomShortcutSet(CommonShortcuts.getMoveHome(), target);
      new MyScrollingAction(table) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          moveEnd(table);
        }
      }.registerCustomShortcutSet(CommonShortcuts.getMoveEnd(), target);
    }
    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveDown(table, e.getModifiers(), cycleScrolling);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveDown(), target);
    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveUp(table, e.getModifiers(), cycleScrolling);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMoveUp(), target);
    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        movePageUp(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMovePageUp(), target);
    new MyScrollingAction(table) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        movePageDown(table);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getMovePageDown(), target);
  }

  static class MoveAction extends AbstractAction {
    private final String myId;
    private final JComponent myComponent;
    private Boolean myCycleScrolling;

    public MoveAction(String id, JComponent component, Boolean cycleScrolling) {
      myId = id;
      myComponent = component;
      myCycleScrolling = cycleScrolling;
    }

    public MoveAction(String id, JComponent component) {
      this(id, component, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final int modifiers = e.getModifiers();
        if (SCROLLUP_ACTION_ID.equals(myId)) doPageUp();
        else if (SCROLLDOWN_ACTION_ID.equals(myId)) doPageDown();
        else if (SELECT_PREVIOUS_ROW_ACTION_ID.equals(myId)) doMoveUp(modifiers);
        else if (SELECT_NEXT_ROW_ACTION_ID.equals(myId)) doMoveDown(modifiers);
        else if (SELECT_LAST_ROW_ACTION_ID.equals(myId)) doMoveEnd();
        else if (SELECT_FIRST_ROW_ACTION_ID.equals(myId)) doMoveHome();
        else if (MOVE_END_ID.equals(myId)) doMoveEnd();
        else if (MOVE_HOME_ID.equals(myId)) doMoveHome();
    }

    private void doMoveEnd() {
      if (myComponent instanceof JList) moveEnd((JList)myComponent);
      else if (myComponent instanceof JTable) moveEnd((JTable)myComponent);
      else throw new IllegalArgumentException("MoveEnd is not implemented for " + myComponent.getClass());
    }

    private void doMoveHome() {
      if (myComponent instanceof JList) moveHome((JList)myComponent);
      else if (myComponent instanceof JTable) moveHome((JTable)myComponent);
      else throw new IllegalArgumentException("MoveHome is not implemented for " + myComponent.getClass());
    }

    private void doMoveUp(@JdkConstants.InputEventMask int modifiers) {
      if (myComponent instanceof JList) moveUp((JList)myComponent, modifiers);
      else if (myComponent instanceof JTable) moveUp((JTable)myComponent, modifiers, isCycleScrolling());
      else throw new IllegalArgumentException("MoveUp is not implemented for " + myComponent.getClass());
    }

    private void doMoveDown(@JdkConstants.InputEventMask int modifiers) {
      if (myComponent instanceof JList) moveDown((JList)myComponent, modifiers);
      else if (myComponent instanceof JTable) moveDown((JTable)myComponent, modifiers, isCycleScrolling());
      else throw new IllegalArgumentException("MoveDown is not implemented for " + myComponent.getClass());
    }

    private void doPageUp() {
      if (myComponent instanceof JList) movePageUp((JList)myComponent);
      else if (myComponent instanceof JTable) movePageUp((JTable)myComponent);
      else throw new IllegalArgumentException("PageUp is not implemented for " + myComponent.getClass());
    }

    private void doPageDown() {
      if (myComponent instanceof JList) movePageDown((JList)myComponent);
      else if (myComponent instanceof JTable) movePageDown((JTable)myComponent);
      else throw new IllegalArgumentException("PageDown is not implemented for " + myComponent.getClass());
    }

    private boolean isCycleScrolling() {
      return myCycleScrolling == null ? UISettings.getInstance().getCycleScrolling() : myCycleScrolling.booleanValue();
    }
  }
}
