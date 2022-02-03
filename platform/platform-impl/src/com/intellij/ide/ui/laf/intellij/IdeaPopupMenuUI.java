// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
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
  private static final JBValue CORNER_RADIUS_X = new JBValue.UIInteger("PopupMenu.borderCornerRadiusX", 8);
  private static final JBValue CORNER_RADIUS_Y = new JBValue.UIInteger("PopupMenu.borderCornerRadiusY", 8);

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

  @Override
  public boolean isPopupTrigger(final MouseEvent event) {
    return event.isPopupTrigger();
  }

  @Override
  public void paint(final Graphics g, final JComponent jcomponent) {
    if (!isUnderPopup(jcomponent)) {
      super.paint(g, jcomponent);
      return;
    }

    UISettings.setupAntialiasing(g);
    Rectangle rectangle = popupMenu.getBounds();
    int cornerRadiusX = CORNER_RADIUS_X.get();
    int cornerRadiusY = CORNER_RADIUS_Y.get();

    g.setColor(popupMenu.getBackground());
    JBColor borderColor = JBColor.namedColor("Menu.borderColor", new JBColor(Gray.xCD, Gray.x51));
    if (isRoundBorder()) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fillRoundRect(0, 0, rectangle.width - 1, rectangle.height - 1, cornerRadiusX, cornerRadiusY);
      g.setColor(borderColor);
      g.drawRoundRect(0, 0, rectangle.width - 1, rectangle.height - 1, cornerRadiusX, cornerRadiusY);
    }
    else {
      int delta = SystemInfoRt.isMac ? 0 : 1;
      g.fillRect(0, 0, rectangle.width - delta, rectangle.height - delta);
      g.setColor(borderColor);
      g.drawRect(0, 0, rectangle.width - delta, rectangle.height - delta);
    }
  }

  public static boolean isRoundBorder() {
    return Registry.is("popup.menu.roundBorder.enabled", false);
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