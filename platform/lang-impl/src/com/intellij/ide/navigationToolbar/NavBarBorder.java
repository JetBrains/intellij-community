// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.NavBarLocation;
import com.intellij.ide.ui.UISettings;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;

import javax.swing.border.Border;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public final class NavBarBorder implements Border {
  private static final Color BORDER_COLOR = JBColor.namedColor("NavBar.borderColor", new JBColor(Gray.xCD, Gray.x51));
  private static final JBValue BW = new JBValue.Float(1);

  @Override
  public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
    UISettings uiSettings = UISettings.getInstance();
    if (ExperimentalUI.isNewUI() && uiSettings.getShowNavigationBar() && uiSettings.getNavBarLocation() == NavBarLocation.TOP
        || !ExperimentalUI.isNewUI() && uiSettings.getShowMainToolbar()) {
      g.setColor(BORDER_COLOR);
      g.fillRect(x, y, width, BW.get());
    }
  }

  @Override
  public Insets getBorderInsets(final Component c) {
    var settings = UISettings.getInstance();
    if (ExperimentalUI.isNewUI() && settings.getShowNavigationBar()) {
      if (settings.getNavBarLocation() == NavBarLocation.TOP) {
        return JBUI.CurrentTheme.NavBar.itemInsets();
      }
      else {
        return JBUI.CurrentTheme.StatusBar.Breadcrumbs.navBarInsets();
      }
    }
    else if (!settings.getShowMainToolbar()) {
      return JBUI.insets(1, 0, 1, 4);
    }
    return JBUI.insets(1, 0, 0, 4);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
