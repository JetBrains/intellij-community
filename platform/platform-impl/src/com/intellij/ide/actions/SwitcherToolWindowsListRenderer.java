/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class SwitcherToolWindowsListRenderer extends ColoredListCellRenderer {
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

  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    hide = false;
    setPaintFocusBorder(false);
    if (value instanceof ToolWindow) {
      final ToolWindow tw = (ToolWindow)value;
      setIcon(getIcon(tw));
      final String name;

      String stripeTitle = tw.getStripeTitle();
      String shortcut = shortcuts.get(tw);
      if (myPinned || shortcut == null) {
        name = stripeTitle;
      } else {
        append(shortcut, new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null));
        name = ": " + stripeTitle;
      }

      append(name);
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
        hide = mySpeedSearch.matchingFragments(stripeTitle) == null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix());
      }
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

    icon = IconUtil.toSize(icon, 16, 16);
    return icon;
  }
}
