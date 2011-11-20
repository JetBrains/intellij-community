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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;

import javax.swing.border.Border;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
class NavBarBorder implements Border {
  private final boolean myDocked;
  private final int myRightOffset;

  public NavBarBorder(boolean docked, int rightOffset) {
    myDocked = docked;
    myRightOffset = rightOffset;
  }

  public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
    if (!myDocked) return;
    
    if (UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
      g.setColor(new Color(255, 255, 255, 120));
      g.drawLine(x, y, x + width, y);

      g.setColor(new Color(0, 0, 0, 50));
      g.drawLine(x, y + 1, x + width, y + 1);
    } else if (NavBarRootPaneExtension.runToolbarExists()) {
      g.setColor(new Color(0, 0, 0, 50));
      g.drawLine(x, y, x + width, y);
    }
    
    if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR && NavBarRootPaneExtension.runToolbarExists()) {
      g.drawLine(x + width - 1, y + 1, x + width - 1, y + height - 2);
      g.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
    }
    
  }

  public Insets getBorderInsets(final Component c) {
    if (myDocked) {
      if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
        if (NavBarRootPaneExtension.runToolbarExists()) {
          return new Insets(1, 0, 1, 4);
        }
        
        return new Insets(0, 0, 0, 4);
      }
      
      return new Insets(2, 0, 0, 4);
    }

    return new Insets(1, 0, 1, 4);
  }

  public boolean isBorderOpaque() {
    return false;
  }
}
