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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.Gray;

import javax.swing.border.Border;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public class NavBarBorder implements Border {
  public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
    if (UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
      g.setColor(Gray._0.withAlpha(50));
      g.drawLine(x, y, x + width, y);
    }
  }

  @Override
  public Insets getBorderInsets(final Component c) {
    if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
      if (NavBarRootPaneExtension.runToolbarExists()) {
        return new Insets(1, 0, 1, 4);
      }
      return new Insets(0, 0, 0, 4);
    }
    return new Insets(1, 0, 0, 4);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
