/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public class NavBarBorder implements Border {
  private static final Color BORDER_COLOR = JBColor.namedColor("NavBar.borderColor", new JBColor(Gray.xCD, Gray.x51));
  private static final Insets NO_RUN_INSETS = JBUI.insets("NavBar.Breadcrumbs.itemInsets", JBUI.insets(4, 2));
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
        return NO_RUN_INSETS;
      }
      else {
        return JBUI.insets(3, 0, 4, 4);
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
