// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public final class IdeaPopupMenuUI extends BasicPopupMenuUI {
  public static final JBValue CORNER_RADIUS = new JBValue.UIInteger("PopupMenu.borderCornerRadius", 8);

  public IdeaPopupMenuUI() {
  }

  public static ComponentUI createUI(final JComponent c) {
    return new IdeaPopupMenuUI();
  }

  public static boolean isUnderPopup(Component component) {
    if (component instanceof JBPopupMenu) {
      Component invoker = ((JPopupMenu)component).getInvoker();
      if (invoker instanceof ActionMenu) {
        return !((ActionMenu)invoker).isMainMenuPlace();
      }
      return true;
    }
    return false;
  }

  public static boolean isPartOfPopupMenu(Component c) {
    if (c == null) {
      return false;
    }
    if (c instanceof JPopupMenu) {
      return isUnderPopup(c);
    }
    return isPartOfPopupMenu(c.getParent());
  }

  public static boolean isMenuBarItem(Component c) {
    return c.getParent() instanceof JMenuBar;
  }

  @Override
  public boolean isPopupTrigger(final MouseEvent event) {
    return event.isPopupTrigger();
  }

  @Override
  public void paint(final Graphics g, final JComponent jcomponent) {
    if (!isUnderPopup(jcomponent) || isRoundBorder()) {
      Rectangle bounds = popupMenu.getBounds();
      g.setColor(popupMenu.getBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      return;
    }

    UISettings.setupAntialiasing(g);

    Rectangle bounds = popupMenu.getBounds();
    JBColor borderColor = JBColor.namedColor("Menu.borderColor", new JBColor(Gray.xCD, Gray.x51));
    int delta = SystemInfoRt.isMac ? 0 : 1;

    g.setColor(popupMenu.getBackground());
    g.fillRect(0, 0, bounds.width - delta, bounds.height - delta);
    g.setColor(borderColor);
    g.drawRect(0, 0, bounds.width - delta, bounds.height - delta);
  }

  public static boolean isRoundBorder() {
    return SystemInfoRt.isMac && ExperimentalUI.isNewUI();
  }

  public static boolean hideEmptyIcon(Component c) {
    if (!isPartOfPopupMenu(c)) {
      return false;
    }
    Container parent = c.getParent();
    if (parent instanceof JPopupMenu) {
      int count = parent.getComponentCount();
      for (int i = 0; i < count; i++) {
        Component item = parent.getComponent(i);
        if (item instanceof JMenuItem) {
          JMenuItem menuItem = (JMenuItem)item;
          Icon icon = menuItem.isEnabled() ? menuItem.getIcon() : menuItem.getDisabledIcon();
          if (icon != null) {
            return false;
          }
        }
      }
    }
    return true;
  }
}