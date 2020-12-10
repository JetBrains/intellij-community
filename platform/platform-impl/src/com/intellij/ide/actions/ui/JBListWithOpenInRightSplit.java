// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.openapi.actionSystem.*;
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

import static com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext;

public class JBListWithOpenInRightSplit<T> extends JBList<T> {

  @NotNull
  public static <T> JBList<T> createListWithOpenInRightSplitter(@NotNull Condition<? super T> isSplitterAvailable) {
    return createListWithOpenInRightSplitter(createDefaultListModel(), isSplitterAvailable);
  }

  @NotNull
  public static <T> JBList<T> createListWithOpenInRightSplitter(@NotNull ListModel<T> dataModel,
                                                                @Nullable Condition<? super T> checkRightSplitter) {
    return createListWithOpenInRightSplitter(dataModel, checkRightSplitter, false);
  }

  @NotNull
  public static <T> JBList<T> createListWithOpenInRightSplitter(@NotNull ListModel<T> dataModel,
                                                                @Nullable Condition<? super T> checkRightSplitter,
                                                                boolean showIconInVisibleArea) {
    return Registry.is("lists.use.open.in.right.splitter")
           ? new JBListWithOpenInRightSplit<>(dataModel, checkRightSplitter, showIconInVisibleArea)
           : new JBList<>(dataModel);
  }

  private class JBFileMouseHandler extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      myMousePoint = e != null ? e.getPoint() : null;
      updateHover();
    }

    @Override
    public void mouseExited(MouseEvent e) {
      myMousePoint = null;
      updateHover();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      myMousePoint = e != null ? e.getPoint() : null;
      updateHover();
    }

    private void updateHover() {
      boolean isHovered = isHovered();
      setCursor(isHovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
      if (myTooltip != null && !isHovered) {
        HelpTooltip.hide(JBListWithOpenInRightSplit.this);
      }
    }

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
  }

  private boolean isHovered() {
    int index = myMousePoint == null ? -1 : locationToIndex(myMousePoint);
    if (index == -1) return false;
    T at = getModel().getElementAt(index);
    if (!canOpenInSplitter(at)) return false;

    return getIconRectangle(index).contains(myMousePoint);
  }

  @Nullable
  private Point myMousePoint;
  @Nullable
  private final HelpTooltip myTooltip;
  @Nullable
  private final Condition<? super T> myCheckRightSplitter;
  private final boolean myShowIconInVisibleArea;

  public JBListWithOpenInRightSplit(@NotNull ListModel<T> dataModel, @Nullable Condition<? super T> checkRightSplitter, boolean showIconInVisibleArea) {
    super(dataModel);
    myCheckRightSplitter = checkRightSplitter;
    myShowIconInVisibleArea = showIconInVisibleArea;
    JBFileMouseHandler handler = new JBFileMouseHandler();

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

    addMouseListener(handler);
    addMouseMotionListener(handler);
  }


  @Override
  public void repaint(long tm, int x, int y, int width, int height) {
    super.repaint(tm, x, y, width, height);
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);

    boolean isSelectedUnderMouse = false;
    if (myMousePoint != null) {
      int index = locationToIndex(myMousePoint);
      if (index != -1 && canOpenInSplitter(getModel().getElementAt(index))) {
        isSelectedUnderMouse = getSelectedIndex() == index;
        final Rectangle iconRect = getIconRectangle(index);
        boolean isIconHover = myMousePoint != null && iconRect.contains(myMousePoint);
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

  protected boolean canOpenInSplitter(@NotNull T item) {
    return myCheckRightSplitter == null || myCheckRightSplitter.value(item);
  }

  @NotNull
  protected Rectangle getIconRectangle(int index) {
    Rectangle bounds = getCellBounds(index, index);
    if (myShowIconInVisibleArea) {
      Rectangle visibleRect = getVisibleRect();
      visibleRect.setSize(visibleRect.width - getInsets().right, visibleRect.height);
      bounds = bounds.intersection(visibleRect);
    }
    Icon icon = toSize(getIcon());
    return new Rectangle(((int) bounds.getMaxX()) - icon.getIconWidth(),
                         bounds.y + (bounds.height - icon.getIconHeight()) / 2,
                         icon.getIconWidth(), icon.getIconHeight());
  }

  @NotNull
  private static Icon toSize(@NotNull Icon icon) {
    Dimension defaultSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;
    return IconUtil.toSize(icon, (int)defaultSize.getWidth(), (int)defaultSize.getHeight());
  }

  protected void invokeAction() {
    HelpTooltip.dispose(this);
    
    AnAction action = ActionManager.getInstance().getAction(getActionId());
    DataContext context = DataManager.getInstance().getDataContext(this);
    action.actionPerformed(createFromDataContext(getClass().getName(), null, context));
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = new Dimension(super.getPreferredSize());
    size.width += ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width;
    return size;
  }

  protected @NotNull @NonNls String getActionId() {
    return IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT;
  }

  protected @NotNull Icon getIcon() {
    return AllIcons.Actions.SplitVertically;
  }
}