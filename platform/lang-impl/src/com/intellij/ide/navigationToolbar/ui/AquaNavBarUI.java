/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class AquaNavBarUI extends AbstractNavBarUI {
  @Override
  public Font getElementFont(NavBarItem navBarItem) {
    return UIUtil.getLabelFont().deriveFont(11.0f);
  }

  @Override
  public boolean isDrawMacShadow(boolean selected, boolean focused) {
    return !selected;
  }

  @Override
  public void doPaintWrapperPanel(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible) {
    if (mainToolbarVisible) {
      g.setColor(new Color(200, 200, 200));
      g.fillRect(0, 0, bounds.width, bounds.height);
    } else {
      UIUtil.drawGradientHToolbarBackground(g, bounds.width, bounds.height);
      g.setColor(new Color(0, 0, 0, 90));
      g.drawLine(0, bounds.height - 1, bounds.width, bounds.height - 1);
      g.setColor(new Color(0, 0, 0, 20));
      g.drawLine(0, bounds.height, bounds.width, bounds.height);
    }
  }

  @Override
  public Insets getWrapperPanelInsets(Insets i) {
    return new Insets(i.top, i.left, i.bottom + 1, i.right);
  }

  @Override
  public void doPaintNavBarPanel(Graphics2D g, Rectangle r, boolean mainToolbarVisible, boolean undocked) {
    if (mainToolbarVisible) {
      g.setPaint(new GradientPaint(0, 0, new Color(240, 240, 240), 0, r.height, new Color(210, 210, 210)));
      g.fillRect(0, 0, r.width, r.height);
    } else {
      UIUtil.drawGradientHToolbarBackground(g, r.width, r.height);
    }
    if (!undocked) {
      g.setColor(new Color(255, 255, 255, 220));
      g.drawLine(0, 1, r.width, 1);
    }

    g.setColor(UIUtil.getBorderColor());
    if (!undocked) g.drawLine(0, 0, r.width, 0);
    g.drawLine(0, r.height-1, r.width, r.height-1);

    if (!mainToolbarVisible) {
      UIUtil.drawDottedLine(g, r.width - 1, 0, r.width - 1, r.height, null, Color.GRAY);
    }
  }
}
