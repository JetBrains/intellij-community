/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui.popup.list;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

/**
 * User: anna
 * Date: 08-Nov-2005
 */
public class DottedBorder implements Border {
  private final Insets myInsets;
  private final Color myColor;

  public DottedBorder(Insets insets, Color color) {
    myInsets = insets;
    myColor = color;
  }

  public DottedBorder(Color color) {
    myInsets = new Insets(1, 1, 1, 1);
    myColor = color;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(myColor);

    if (ApplicationManager.getApplication() != null) {
      UIUtil.drawDottedRectangle(g, x, y, x + width - 1, y + height - 1);
    }
  }

  public Insets getBorderInsets(Component c) {
    return myInsets;
  }

  public boolean isBorderOpaque() {
    return true;
  }
}
