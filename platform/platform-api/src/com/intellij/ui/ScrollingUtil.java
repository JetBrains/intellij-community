// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * A helper class for registering Keymap aware navigation actions for lists and tables
 *
 * @author Konstantin Bulenkov
 */
public final class ScrollingUtil {
  private static final Logger LOG = Logger.getInstance(ScrollingUtil.class);
  private static final @NonNls String SCROLL_UP_ACTION_ID = "scrollUp";
  private static final @NonNls String SCROLL_DOWN_ACTION_ID = "scrollDown";
  private static final @NonNls String SELECT_PREVIOUS_ROW_ACTION_ID = "selectPreviousRow";
  private static final @NonNls String SELECT_NEXT_ROW_ACTION_ID = "selectNextRow";
  private static final @NonNls String SELECT_LAST_ROW_ACTION_ID = "selectLastRow";
  private static final @NonNls String SELECT_FIRST_ROW_ACTION_ID = "selectFirstRow";
  private static final @NonNls String MOVE_HOME_ID = "MOVE_HOME";
  private static final @NonNls String MOVE_END_ID = "MOVE_END";

  public static final int ROW_PADDING = 2;

  public static void selectItem(@NotNull JList<?> list, int index) {
    LOG.assertTrue(index >= 0);
    LOG.assertTrue(index < list.getModel().getSize());
    ensureIndexIsVisible(list, index, 0);
    list.setSelectedIndex(index);
  }

  public static void ensureSelectionExists(@NotNull JList<?> list) {
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

  public static <T> boolean selectItem(@NotNull JList<T> list, @NotNull T item) {
    ListModel<T> model = list.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      T anItem = model.getElementAt(i);
      if (item.equals(anItem)) {
        selectItem(list, i);
        return true;
      }
    }
    return false;
  }

  public static void movePageUp(JList<?> list) {
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

  public static void movePageDown(@NotNull JList<?> list) {
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

  public static void moveHome(@NotNull JList<?> list) {
    list.setSelectedIndex(0);
    list.ensureIndexIsVisible(0);
  }

  public static void moveEnd(@NotNull JList<?> list) {
    int index = list.getModel().getSize() - 1;
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void ensureIndexIsVisible(@NotNull JList<?> list, int index, int moveDirection) {
    _ensureIndexIsVisible(list, index, moveDirection, list.getModel().getSize());
  }

  public static void ensureIndexIsVisible(@NotNull JTable table, int index, int moveDirection) {
    _ensureIndexIsVisible(table, index, moveDirection, table.getRowCount());
  }

  private static void _ensureIndexIsVisible(@NotNull JComponent c, int index, int moveDirection, int size) {
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

  public static void ensureRangeIsVisible(@NotNull JList<?> list, int top, int bottom) {
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
  private static void _ensureRangeIsVisible(@NotNull JComponent c, int top, int bottom) {
    if (c instanceof JList) {
      ensureRangeIsVisible((JList<?>)c, top, bottom);
    }
    else if (c instanceof JTable table) {
      Rectangle cellBounds = getCellBounds(table, top, bottom);
      cellBounds.x = 0;
      table.scrollRectToVisible(cellBounds);
    }
  }

  public static boolean isIndexFullyVisible(@NotNull JList<?> list, int index) {
    int first = list.getFirstVisibleIndex();
    int last = list.getLastVisibleIndex();

    if (first < 0 || last < 0 || index < first || index > last) {
      return false;
    }
    if (index > first && index < last) {
      return true;
    }

    return list.getVisibleRect().contains(list.getCellBounds(index, index));
  }

  private static int getVisibleRowCount(@NotNull JList<?> list) {
    return list.getLastVisibleIndex() - list.getFirstVisibleIndex() + 1;
  }

  public static void moveDown(@NotNull JList<?> list, @JdkConstants.InputEventMask int modifiers) {
    moveDown(list, modifiers, UISettings.getInstance().getCycleScrolling());
  }

  private static void moveDown(@NotNull JList<?> list, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _moveDown(list, list.getSelectionModel(), modifiers, list.getModel().getSize(), cycleScrolling);
  }

  private static void selectOrAddSelection(@NotNull ListSelectionModel selectionModel,
                                           int indexToSelect,
                                           @JdkConstants.InputEventMask int modifiers) {
    if (selectionModel.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) {
      selectionModel.setSelectionInterval(indexToSelect,indexToSelect);
    }
    else {
      if ((modifiers & InputEvent.SHIFT_DOWN_MASK) == 0) {
        selectionModel.setSelectionInterval(indexToSelect, indexToSelect);
      }
      else {
        selectionModel.addSelectionInterval(indexToSelect, indexToSelect);
      }
    }
  }

  public static void installActions(@NotNull JList<?> list) {
    installActions(list, null);
  }

  public static void installActions(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    installActions(list, focusParent, UISettings.getInstance().getCycleScrolling());
  }

  public static void installActions(final @NotNull JList<?> list, @Nullable JComponent focusParent, boolean cycleScrolling) {
    ActionMap actionMap = list.getActionMap();
    setupScrollingActions(list, actionMap, cycleScrolling);
    actionMap.put(MOVE_HOME_ID, new MoveAction(MOVE_HOME_ID, list));
    actionMap.put(MOVE_END_ID, new MoveAction(MOVE_END_ID, list));

    maybeInstallDefaultShortcuts(list);

    installMoveUpAction(list, focusParent, cycleScrolling);
    installMoveDownAction(list, focusParent, cycleScrolling);
    installMovePageUpAction(list, focusParent);
    installMovePageDownAction(list, focusParent);
    if (!(focusParent instanceof JTextComponent)) {
      installMoveHomeAction(list, focusParent);
      installMoveEndAction(list, focusParent);
    }
  }

  private static void setupScrollingActions(@NotNull JComponent list, ActionMap actionMap, boolean cycleScrolling) {
    actionMap.put(SCROLL_UP_ACTION_ID, new MoveAction(SCROLL_UP_ACTION_ID, list, cycleScrolling));
    actionMap.put(SCROLL_DOWN_ACTION_ID, new MoveAction(SCROLL_DOWN_ACTION_ID, list, cycleScrolling));
    actionMap.put(SELECT_PREVIOUS_ROW_ACTION_ID, new MoveAction(SELECT_PREVIOUS_ROW_ACTION_ID, list, cycleScrolling));
    actionMap.put(SELECT_NEXT_ROW_ACTION_ID, new MoveAction(SELECT_NEXT_ROW_ACTION_ID, list, cycleScrolling));
    actionMap.put(SELECT_LAST_ROW_ACTION_ID, new MoveAction(SELECT_LAST_ROW_ACTION_ID, list, cycleScrolling));
    actionMap.put(SELECT_FIRST_ROW_ACTION_ID, new MoveAction(SELECT_FIRST_ROW_ACTION_ID, list, cycleScrolling));
  }

  public static void redirectExpandSelection(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    if (focusParent != null && focusParent != list) {
      focusParent.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.isShiftDown()) {
            if (e.getKeyCode() == KeyEvent.VK_DOWN) {
              list.dispatchEvent(e);
              e.consume();
            }
            if (e.getKeyCode() == KeyEvent.VK_UP) {
              list.dispatchEvent(e);
              e.consume();
            }
          }
        }
      });
    }
  }

  public static void installMoveEndAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    new ListMoveEndAction(focusParent, list);
  }

  public static void installMoveHomeAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    new ListMoveHomeAction(focusParent, list);
  }

  public static void installMovePageDownAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    new ListMovePageDownAction(focusParent, list);
  }

  public static void installMovePageUpAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    new ListMovePageUpAction(focusParent, list);
  }

  public static void installMoveDownAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    installMoveDownAction(list, focusParent, UISettings.getInstance().getCycleScrolling());
  }

  private static void installMoveDownAction(@NotNull JList<?> list, @Nullable JComponent focusParent, boolean cycleScrolling) {
    new ListMoveDownAction(focusParent, list, cycleScrolling);
  }

  public static void installMoveUpAction(@NotNull JList<?> list, @Nullable JComponent focusParent) {
    installMoveUpAction(list, focusParent, UISettings.getInstance().getCycleScrolling());
  }

  private static void installMoveUpAction(@NotNull JList<?> list, @Nullable JComponent focusParent, boolean cycleScrolling) {
    new ListMoveUpAction(focusParent, list, cycleScrolling);
  }

  private static void maybeInstallDefaultShortcuts(@NotNull JComponent component) {
    InputMap map = component.getInputMap(JComponent.WHEN_FOCUSED);
    UIUtil.maybeInstall(map, SCROLL_UP_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
    UIUtil.maybeInstall(map, SCROLL_DOWN_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_PREVIOUS_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    UIUtil.maybeInstall(map, SELECT_NEXT_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    UIUtil.maybeInstall(map, SELECT_FIRST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
    UIUtil.maybeInstall(map, SELECT_LAST_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));
    UIUtil.maybeInstall(map, MOVE_HOME_ID, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    UIUtil.maybeInstall(map, MOVE_END_ID, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
  }

  public interface ScrollingAction extends DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  }

  public abstract static class ListScrollAction extends MyScrollingAction {
    protected ListScrollAction(@NotNull ShortcutSet shortcutSet, @NotNull JComponent component) {
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

  private static @NotNull Rectangle getCellBounds(@NotNull JTable table, int top, int bottom) {
    return table.getCellRect(top, 0, true).union(table.getCellRect(bottom,0,true));
  }

  private static int visibleRowCount(JComponent c) {
    if (c instanceof JList) return getVisibleRowCount((JList<?>)c);
    if (c instanceof JTable) return getVisibleRowCount((JTable)c);
    return -1;
  }

  private static int getVisibleRowCount(@NotNull JTable table) {
    Rectangle visibleRect = table.getVisibleRect();
    return getTrailingRow(table, visibleRect) - getLeadingRow(table, visibleRect) + 1;
  }

  public static @NotNull Couple<Integer> getVisibleRows(@NotNull JTable table) {
    Rectangle visibleRect = table.getVisibleRect();
    return Couple.of(getLeadingRow(table, visibleRect), getTrailingRow(table, visibleRect));
  }

  private static int getLeadingRow(@NotNull JTable table, @NotNull Rectangle visibleRect) {
    int row = table.rowAtPoint(getLeadingPoint(table, visibleRect));
    if (row >= 0) return row;
    // return the first row in the table
    // if there is no any row at the given point
    return 0 < table.getRowCount() ? 0 : -1;
  }

  private static @NotNull Point getLeadingPoint(@NotNull JTable table, @NotNull Rectangle visibleRect) {
    if (table.getComponentOrientation().isLeftToRight()) {
        return new Point(visibleRect.x, visibleRect.y);
    }
    else {
        return new Point(visibleRect.x + visibleRect.width,
                                 visibleRect.y);
    }
  }

  public static int getReadableRow(@NotNull JTable table, int maximumHiddenPart) {
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

  public static boolean isVisible(@NotNull JTable table, int row) {
    Rectangle visibleRect = table.getVisibleRect();
    int start = getLeadingRow(table, visibleRect);
    int end = getTrailingRow(table, visibleRect);
    return row >= start && row <= end;
  }

  private static int getTrailingRow(@NotNull JTable table, @NotNull Rectangle visibleRect) {
      Point trailingPoint;

      if (table.getComponentOrientation().isLeftToRight()) {
          trailingPoint = new Point(visibleRect.x,
                                    visibleRect.y + visibleRect.height - 1);
      }
      else {
          trailingPoint = new Point(visibleRect.x + visibleRect.width,
                                    visibleRect.y + visibleRect.height - 1);
      }
    int row = table.rowAtPoint(trailingPoint);
    if (row >= 0) return row;
    // return the last row in the table
    // if there is no any row at the given point
    return table.getRowCount() - 1;
  }


  public static void moveDown(@NotNull JTable table, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _moveDown(table, table.getSelectionModel(), modifiers, table.getRowCount(), cycleScrolling);
  }

  public static void moveUp(@NotNull JList<?> list, @JdkConstants.InputEventMask int modifiers) {
    moveUp(list, modifiers, UISettings.getInstance().getCycleScrolling());
  }

  private static void moveUp(@NotNull JList<?> list, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _moveUp(list, list.getSelectionModel(), list.getModel().getSize(), modifiers, cycleScrolling);
  }

  public static void moveUp(@NotNull JTable table, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _moveUp(table, table.getSelectionModel(), table.getModel().getRowCount(), modifiers, cycleScrolling);
  }

  private static void _moveDown(@NotNull JComponent c, @NotNull ListSelectionModel selectionModel, @JdkConstants.InputEventMask final int modifiers, int size, boolean cycleScrolling) {
    _move(c, selectionModel, modifiers, size, cycleScrolling, +1);
  }

  private static void _move(@NotNull JComponent c, @NotNull ListSelectionModel selectionModel, @JdkConstants.InputEventMask final int modifiers, int size, boolean cycleScrolling, int direction) {
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
    if (selectionModel instanceof ImpossibleListSelectionModel impossibleListSelectionModel &&
        !impossibleListSelectionModel.canBeSelected(indexToSelect)) {
      indexToSelect += direction < 0 ? -1 : 1;
    }
    _ensureIndexIsVisible(c, indexToSelect, direction, size);
    selectOrAddSelection(selectionModel, indexToSelect, modifiers);
  }

  private static void _moveUp(@NotNull JComponent c, @NotNull ListSelectionModel selectionModel, int size, @JdkConstants.InputEventMask int modifiers, boolean cycleScrolling) {
    _move(c, selectionModel, modifiers, size, cycleScrolling, -1);
  }

  public static void moveHome(@NotNull JTable table) {
    table.getSelectionModel().setSelectionInterval(0,0);
    ensureIndexIsVisible(table, 0,0);
  }

  public static void moveEnd(@NotNull JTable table) {
    int index = table.getModel().getRowCount() - 1;
    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void movePageUp(@NotNull JTable table) {
    int visible = getVisibleRowCount(table);
    if (visible <= 0) {
      moveHome(table);
      return;
    }
    int step = visible - 1;
    ListSelectionModel selectionModel = table.getSelectionModel();
    int index = Math.max(selectionModel.getMinSelectionIndex() - step, 0);
    int visibleIndex = getLeadingRow(table, table.getVisibleRect());
    int top = visibleIndex - step;
    if (top < 0) {
      top = 0;
    }
    int bottom = top + visible - 1;
    _scrollAfterPageMove(table, top, bottom, index);
  }

  public static void movePageDown(@NotNull JTable table) {
    int visible = getVisibleRowCount(table);
    if (visible <= 0) {
      moveEnd(table);
      return;
    }
    ListSelectionModel selectionModel = table.getSelectionModel();
    int step = visible - 1;
    int firstVisibleRow = getLeadingRow(table, table.getVisibleRect());
    int top = firstVisibleRow + step;
    int bottom = top + visible - 1;
    int size = table.getModel().getRowCount();
    int index = Math.min(selectionModel.getMinSelectionIndex() + step, size - 1);
    _scrollAfterPageMove(table, top, bottom, index);
  }

  private static void _scrollAfterPageMove(@NotNull JTable table, int top, int bottom, int index) {
    int size = table.getModel().getRowCount();
    if (bottom >= size) {
      bottom = size - 1;
    }
    Rectangle cellBounds = getCellBounds(table, top, bottom);
    table.scrollRectToVisible(cellBounds);
    table.getSelectionModel().setSelectionInterval(index, index);
    ensureIndexIsVisible(table, index, 0);
  }

  public static void installActions(@NotNull JTable table) {
    installActions(table, UISettings.getInstance().getCycleScrolling());
  }

  @ApiStatus.Internal
  public abstract static class MyScrollingAction extends DumbAwareAction implements ScrollingAction, LightEditCompatible {
    private final JComponent myComponent;

    MyScrollingAction(@NotNull JComponent component) {
      myComponent = component;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(isEnabled());
    }

    protected boolean isEnabled() {
      var speedSearch = SpeedSearchSupply.getSupply(myComponent);
      // Check if the speed search supports its own navigation (such as go to next/previous match).
      // If it doesn't, we take over instead.
      return (speedSearch == null || !speedSearch.supportsNavigation()) && !isEmpty(myComponent);
    }
  }

  public static boolean isEmpty(JComponent component) {
    if (component instanceof JTable) return ((JTable)component).getRowCount() < 1;
    if (component instanceof JList) return ((JList<?>)component).getModel().getSize() <1;
    return false;
  }

  public static void installActions(@NotNull JTable table, final boolean cycleScrolling) {
    installActions(table, cycleScrolling, null);
  }

  public static void installActions(final @NotNull JTable table, final boolean cycleScrolling, @Nullable JComponent focusParent) {
    ActionMap actionMap = table.getActionMap();
    setupScrollingActions(table, actionMap, cycleScrolling);

    maybeInstallDefaultShortcuts(table);
    JComponent target = focusParent == null ? table : focusParent;

    new TableMoveHomeAction(table).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), table);
    new TableMoveEndAction(table).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), table);
    if (!(focusParent instanceof JTextComponent)) {
      new TableMoveHomeAction(table).registerCustomShortcutSet(CommonShortcuts.getMoveHome(), target);
      new TableMoveEndAction(table).registerCustomShortcutSet(CommonShortcuts.getMoveEnd(), target);
    }
    new TableMoveDownAction(table, cycleScrolling, target).registerCustomShortcutSet(CommonShortcuts.getMoveDown(), target);
    new TableMoveUpAction(table, cycleScrolling, target).registerCustomShortcutSet(CommonShortcuts.getMoveUp(), target);
    new TableMovePageUpAction(table).registerCustomShortcutSet(CommonShortcuts.getMovePageUp(), target);
    new TableMovePageDownAction(table).registerCustomShortcutSet(CommonShortcuts.getMovePageDown(), target);
  }

  private static boolean isMultiline(@NotNull JComponent component) {
    return component instanceof JTextArea && ((JTextArea)component).getText().contains("\n");
  }

  static class MoveAction extends AbstractAction {
    private final String myId;
    private final JComponent myComponent;
    private final Boolean myCycleScrolling;

    MoveAction(@NotNull String id, @NotNull JComponent component, @Nullable Boolean cycleScrolling) {
      myId = id;
      myComponent = component;
      myCycleScrolling = cycleScrolling;
    }

    MoveAction(@NotNull String id, @NotNull JComponent component) {
      this(id, component, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final int modifiers = e.getModifiers();
        if (SCROLL_UP_ACTION_ID.equals(myId)) doPageUp();
        else if (SCROLL_DOWN_ACTION_ID.equals(myId)) doPageDown();
        else if (SELECT_PREVIOUS_ROW_ACTION_ID.equals(myId)) doMoveUp(modifiers);
        else if (SELECT_NEXT_ROW_ACTION_ID.equals(myId)) doMoveDown(modifiers);
        else if (SELECT_LAST_ROW_ACTION_ID.equals(myId)) doMoveEnd();
        else if (SELECT_FIRST_ROW_ACTION_ID.equals(myId)) doMoveHome();
        else if (MOVE_END_ID.equals(myId)) doMoveEnd();
        else if (MOVE_HOME_ID.equals(myId)) doMoveHome();
    }

    private void doMoveEnd() {
      if (myComponent instanceof JList) moveEnd((JList<?>)myComponent);
      else if (myComponent instanceof JTable) moveEnd((JTable)myComponent);
      else throw new IllegalArgumentException("MoveEnd is not implemented for " + myComponent.getClass());
    }

    private void doMoveHome() {
      if (myComponent instanceof JList) moveHome((JList<?>)myComponent);
      else if (myComponent instanceof JTable) moveHome((JTable)myComponent);
      else throw new IllegalArgumentException("MoveHome is not implemented for " + myComponent.getClass());
    }

    private void doMoveUp(@JdkConstants.InputEventMask int modifiers) {
      if (myComponent instanceof JList) moveUp((JList<?>)myComponent, modifiers, isCycleScrolling());
      else if (myComponent instanceof JTable) moveUp((JTable)myComponent, modifiers, isCycleScrolling());
      else throw new IllegalArgumentException("MoveUp is not implemented for " + myComponent.getClass());
    }

    private void doMoveDown(@JdkConstants.InputEventMask int modifiers) {
      if (myComponent instanceof JList) moveDown((JList<?>)myComponent, modifiers, isCycleScrolling());
      else if (myComponent instanceof JTable) moveDown((JTable)myComponent, modifiers, isCycleScrolling());
      else throw new IllegalArgumentException("MoveDown is not implemented for " + myComponent.getClass());
    }

    private void doPageUp() {
      if (myComponent instanceof JList) movePageUp((JList<?>)myComponent);
      else if (myComponent instanceof JTable) movePageUp((JTable)myComponent);
      else throw new IllegalArgumentException("PageUp is not implemented for " + myComponent.getClass());
    }

    private void doPageDown() {
      if (myComponent instanceof JList) movePageDown((JList<?>)myComponent);
      else if (myComponent instanceof JTable) movePageDown((JTable)myComponent);
      else throw new IllegalArgumentException("PageDown is not implemented for " + myComponent.getClass());
    }

    private boolean isCycleScrolling() {
      return myCycleScrolling == null ? UISettings.getInstance().getCycleScrolling() : myCycleScrolling.booleanValue();
    }
  }

  private static class ListMoveUpAction extends ListScrollAction {
    private final @NotNull JList<?> myList;
    private final boolean myCycleScrolling;

    private ListMoveUpAction(@Nullable JComponent focusParent, @NotNull JList<?> list, boolean cycleScrolling) {
      super(CommonShortcuts.getMoveUp(),
            focusParent == null
            ? list
            : focusParent);
      myList = list;
      myCycleScrolling = cycleScrolling;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveUp(myList, 0, myCycleScrolling);
    }
  }

  private static class ListMoveEndAction extends ListScrollAction {
    private final @NotNull JList<?> myList;

    private ListMoveEndAction(@Nullable JComponent focusParent, @NotNull JList<?> list) {
      super(CommonShortcuts.getMoveEnd(), focusParent == null ? list : focusParent);
      myList = list;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveEnd(myList);
    }
  }

  private static class ListMoveHomeAction extends ListScrollAction {
    private final @NotNull JList<?> myList;

    private ListMoveHomeAction(@Nullable JComponent focusParent, @NotNull JList<?> list) {
      super(CommonShortcuts.getMoveHome(), focusParent == null ? list : focusParent);
      myList = list;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveHome(myList);
    }
  }

  private static class ListMovePageDownAction extends ListScrollAction {
    private final @NotNull JList<?> myList;

    private ListMovePageDownAction(@Nullable JComponent focusParent, @NotNull JList<?> list) {
      super(CommonShortcuts.getMovePageDown(), focusParent == null ? list : focusParent);
      myList = list;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      movePageDown(myList);
    }
  }

  private static class ListMovePageUpAction extends ListScrollAction {
    private final @NotNull JList<?> myList;

    private ListMovePageUpAction(@Nullable JComponent focusParent, @NotNull JList<?> list) {
      super(CommonShortcuts.getMovePageUp(), focusParent == null ? list : focusParent);
      myList = list;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      movePageUp(myList);
    }
  }

  private static class ListMoveDownAction extends ListScrollAction {
    private final @NotNull JList<?> myList;
    private final boolean myCycleScrolling;

    private ListMoveDownAction(@Nullable JComponent focusParent, @NotNull JList<?> list, boolean cycleScrolling) {
      super(CommonShortcuts.getMoveDown(),
            focusParent == null
            ? list
            : focusParent);
      myList = list;
      myCycleScrolling = cycleScrolling;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveDown(myList, 0, myCycleScrolling);
    }
  }

  private static class TableMoveHomeAction extends MyScrollingAction {
    private final @NotNull JTable myTable;

    private TableMoveHomeAction(@NotNull JTable table) {
      super(table);
      myTable = table;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveHome(myTable);
    }
  }

  private static class TableMoveEndAction extends MyScrollingAction {
    private final @NotNull JTable myTable;

    private TableMoveEndAction(@NotNull JTable table) {
      super(table);
      myTable = table;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveEnd(myTable);
    }
  }

  private static class TableMoveDownAction extends MyScrollingAction {
    private final @NotNull JTable myTable;
    private final boolean myCycleScrolling;
    private final JComponent myTarget;

    private TableMoveDownAction(@NotNull JTable table, boolean cycleScrolling, JComponent target) {
      super(table);
      myTable = table;
      myCycleScrolling = cycleScrolling;
      myTarget = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveDown(myTable, e.getModifiers(), myCycleScrolling);
    }

    @Override
    protected boolean isEnabled() {
      return super.isEnabled() && !isMultiline(myTarget);
    }
  }

  private static class TableMoveUpAction extends MyScrollingAction {
    private final @NotNull JTable myTable;
    private final boolean myCycleScrolling;
    private final JComponent myTarget;

    private TableMoveUpAction(@NotNull JTable table, boolean cycleScrolling, JComponent target) {
      super(table);
      myTable = table;
      myCycleScrolling = cycleScrolling;
      myTarget = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      moveUp(myTable, e.getModifiers(), myCycleScrolling);
    }

    @Override
    protected boolean isEnabled() {
      return super.isEnabled() && !isMultiline(myTarget);
    }
  }

  private static class TableMovePageUpAction extends MyScrollingAction {
    private final @NotNull JTable myTable;

    private TableMovePageUpAction(@NotNull JTable table) {
      super(table);
      myTable = table;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      movePageUp(myTable);
    }
  }

  private static class TableMovePageDownAction extends MyScrollingAction {
    private final @NotNull JTable myTable;

    private TableMovePageDownAction(@NotNull JTable table) {
      super(table);
      myTable = table;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      movePageDown(myTable);
    }
  }

  /**
   * Defines a model for list selection, focusing on determining whether an item in the list can be selected.
   */
  @ApiStatus.Internal
  public interface ImpossibleListSelectionModel {
    boolean canBeSelected(int select);
  }
}
