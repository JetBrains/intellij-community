// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.ui.MouseTracker;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class JBListWithOpenInRightSplit<T> extends JBList<T> {

  public static @NotNull <T> JBList<T> createListWithOpenInRightSplitter(@NotNull ListModel<T> dataModel,
                                                                         @Nullable Condition<? super T> checkRightSplitter) {
    return Registry.is("lists.use.open.in.right.splitter")
           ? new JBListWithOpenInRightSplit<>(dataModel, checkRightSplitter)
           : new JBList<>(dataModel);
  }

  private void updateHover() {
    boolean isHovered = isHovered();
    setCursor(isHovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
    if (myTooltip != null && !isHovered) {
      HelpTooltip.hide(this);
    }
  }

  private boolean isHovered() {
    Point mousePoint = mouseTracker.getMousePoint();
    int index = mousePoint == null ? -1 : locationToIndex(mousePoint);
    if (index == -1) return false;
    T at = getModel().getElementAt(index);
    if (!canOpenInSplitter(at)) return false;

    return getIconRectangle(index).contains(mousePoint);
  }

  private final MouseTracker mouseTracker;

  private final @Nullable HelpTooltip myTooltip;
  private final @Nullable Condition<? super T> myCheckRightSplitter;

  public JBListWithOpenInRightSplit(@NotNull ListModel<T> dataModel, @Nullable Condition<? super T> checkRightSplitter) {
    super(dataModel);
    myCheckRightSplitter = checkRightSplitter;
    mouseTracker = new MouseTracker(this);
    mouseTracker.addMoveListener(new MouseTracker.MoveListener() {
      @Override
      public void changed(@Nullable Point oldMousePoint, @Nullable Point newMousePoint) {
        updateHover();
      }
    });
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        Point point = e.getPoint();
        int index = locationToIndex(point);

        //alt + click is "OpenInRightSplit", or click the icon
        if (index != -1 && getIconRectangle(index).contains(point)) {
          invokeAction();
          e.consume();
        }
      }
    });

    AnAction action = ActionManager.getInstance().getAction(getActionId());
    if (action != null) {
      String text = KeymapUtil.getFirstKeyboardShortcutText(action);
      myTooltip = new HelpTooltip().setTitle(StringUtil.notNullize(action.getTemplatePresentation().getText())).setShortcut(text);
      myTooltip.installOn(this);
      HelpTooltip.setMasterPopupOpenCondition(this, () -> {
        return isHovered();
      });
    }
    else {
      myTooltip = null;
    }
    if (action != null) {
      OpenInRightSplitAction.Companion.overrideDoubleClickWithOneClick(this);
    }
  }


  @Override
  public void paint(Graphics g) {
    super.paint(g);

    boolean isSelectedUnderMouse = false;
    Point mousePoint = mouseTracker.getMousePoint();

    if (mousePoint != null) {
      int index = locationToIndex(mousePoint);
      if (index != -1 && canOpenInSplitter(getModel().getElementAt(index))) {
        isSelectedUnderMouse = getSelectedIndex() == index;
        final Rectangle iconRect = getIconRectangle(index);
        boolean isIconHover = iconRect.contains(mousePoint);
        Icon icon = getIcon();
        if (isSelectedUnderMouse) {
          icon = IconLoader.getDarkIcon(icon, true);
        }
        if (!isIconHover && !isSelectedUnderMouse) {
          icon = IconLoader.getTransparentIcon(icon);
        }

        icon = toSize(icon);

        icon.paintIcon(this, g, iconRect.x, iconRect.y);
      }
    }

    int index = getSelectedIndex();
    if (index != -1 && !isSelectedUnderMouse && canOpenInSplitter(getModel().getElementAt(index))) {
      final Rectangle iconRect = getIconRectangle(index);
      Icon icon = getIcon();
      icon = IconLoader.getDarkIcon(icon, true);
      icon = toSize(icon);
      icon.paintIcon(this, g, iconRect.x, iconRect.y);
    }
  }

  private boolean canOpenInSplitter(@NotNull T item) {
    return myCheckRightSplitter == null || myCheckRightSplitter.value(item);
  }

  private @NotNull Rectangle getIconRectangle(int index) {
    Rectangle bounds = getCellBounds(index, index);
    Rectangle visibleRect = getVisibleRect();
    visibleRect.setSize(visibleRect.width - getInsets().right, visibleRect.height);
    bounds = bounds.intersection(visibleRect);
    Icon icon = toSize(getIcon());
    return new Rectangle(((int) bounds.getMaxX()) - icon.getIconWidth(),
                         bounds.y + (bounds.height - icon.getIconHeight()) / 2,
                         icon.getIconWidth(), icon.getIconHeight());
  }

  private static @NotNull Icon toSize(@NotNull Icon icon) {
    Dimension defaultSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;
    return IconUtil.toSize(icon, (int)defaultSize.getWidth(), (int)defaultSize.getHeight());
  }

  private void invokeAction() {
    HelpTooltip.dispose(this);

    AnAction action = ActionManager.getInstance().getAction(getActionId());
    ActionUtil.invokeAction(action, this, getClass().getName(), null, null);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = new Dimension(super.getPreferredSize());
    size.width += ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width;
    return size;
  }

  private static @NotNull @NonNls String getActionId() {
    return IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT;
  }

  private static @NotNull Icon getIcon() {
    return AllIcons.Actions.SplitVertically;
  }
}