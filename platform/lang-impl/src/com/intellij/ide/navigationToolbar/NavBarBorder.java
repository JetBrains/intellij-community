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

    g.setColor(c.getBackground() != null ? c.getBackground().darker() : Color.darkGray);

    boolean drawTopBorder = true;
    if (myDocked) {
      if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
        drawTopBorder = false;
      }
    }

    if (myRightOffset == -1) {
      if (drawTopBorder) {
        g.drawLine(0, 0, width - 1, 0);
      }
    }
    else {
      if (drawTopBorder) {
        g.drawLine(0, 0, width - myRightOffset + 3, 0);
      } else {
        g.drawLine(width - 1, 0, width - 1, height);
      }
    }
    g.drawLine(0, height - 1, width, height - 1);

    if (myRightOffset == -1) {
      g.drawLine(0, 0, 0, height);
      g.drawLine(width - 1, 0, width - 1, height - 1);
    }
  }

  public Insets getBorderInsets(final Component c) {
    return new Insets(3, 4, 3, 4);
  }

  public boolean isBorderOpaque() {
    return false;
  }
}
