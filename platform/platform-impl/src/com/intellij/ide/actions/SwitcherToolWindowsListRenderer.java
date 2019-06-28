// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class SwitcherToolWindowsListRenderer extends ColoredListCellRenderer<ToolWindow> {
  private final SpeedSearchBase mySpeedSearch;
  private final Map<ToolWindow, String> shortcuts;
  private final boolean myPinned;
  private boolean hide = false;

  SwitcherToolWindowsListRenderer(SpeedSearchBase speedSearch,
                                  Map<ToolWindow, String> shortcuts, boolean pinned) {
    mySpeedSearch = speedSearch;
    this.shortcuts = shortcuts;
    myPinned = pinned;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends ToolWindow> list,
                                       ToolWindow tw,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
    hide = false;
    setPaintFocusBorder(false);
    setIcon(getIcon(tw));
    final String name;

    String stripeTitle = tw.getStripeTitle();
    String shortcut = shortcuts.get(tw);
    if (myPinned || shortcut == null) {
      name = stripeTitle;
    }
    else {
      append(shortcut, new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null));
      name = ": " + stripeTitle;
    }

    append(name);
    if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
      hide = mySpeedSearch.matchingFragments(stripeTitle) == null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix());
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    GraphicsConfig config = new GraphicsConfig(g);
    if (hide) {
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
    }
    super.doPaint(g);
    config.restore();
  }

  private static Icon getIcon(ToolWindow toolWindow) {
    Icon icon = toolWindow.getIcon();
    if (icon == null) {
      return PlatformIcons.UI_FORM_ICON;
    }

    icon = IconUtil.toSize(icon, JBUIScale.scale(16), JBUIScale.scale(16));
    return icon;
  }
}
