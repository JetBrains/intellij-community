/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
* @author Konstantin Bulenkov
*/
class SwitcherToolWindowsListRenderer extends ColoredListCellRenderer {
  private static final Map<String, Icon> iconCache = new HashMap<String, Icon>();
  private final SpeedSearchBase mySpeedSearch;
  private final Map<ToolWindow, String> ids;
  private final Map<ToolWindow, String> shortcuts;
  private final boolean myPinned;
  private boolean hide = false;

  SwitcherToolWindowsListRenderer(SpeedSearchBase speedSearch,
                                  Map<ToolWindow, String> ids,
                                  Map<ToolWindow, String> shortcuts, boolean pinned) {
    mySpeedSearch = speedSearch;
    this.ids = ids;
    this.shortcuts = shortcuts;
    myPinned = pinned;
  }

  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    hide = false;
    if (value instanceof ToolWindow) {
      final ToolWindow tw = (ToolWindow)value;
      setIcon(getIcon(tw));
      final String name;

      if (myPinned) {
        name = ids.get(tw);
      } else {
        append(shortcuts.get(tw), new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null));
        name = ": " + ids.get(tw);
      }

      append(name);
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
        hide = mySpeedSearch.matchingFragments(ids.get(tw)) == null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix());
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

  private Icon getIcon(ToolWindow toolWindow) {
    Icon icon = iconCache.get(ids.get(toolWindow));
    if (icon != null) return icon;

    icon = toolWindow.getIcon();
    if (icon == null) {
      return PlatformIcons.UI_FORM_ICON;
    }

    icon = to16x16(icon);
    iconCache.put(ids.get(toolWindow), icon);
    return icon;
  }

  private static Icon to16x16(Icon icon) {
    if (icon.getIconHeight() == 16 && icon.getIconWidth() == 16) return icon;
    final int w = Math.min (icon.getIconWidth(), 16);
    final int h = Math.min(icon.getIconHeight(), 16);

    final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(16, 16, Color.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    final BufferedImage img = UIUtil.createImage(16, 16, BufferedImage.TRANSLUCENT);
    final int offX = Math.max((16 - w) / 2, 0);
    final int offY = Math.max((16 - h) / 2, 0);
    for (int col = 0; col < w; col++) {
      for (int row = 0; row < h; row++) {
        img.setRGB(col + offX, row + offY, image.getRGB(col, row));
      }
    }

    return new ImageIcon(img);
  }
}
