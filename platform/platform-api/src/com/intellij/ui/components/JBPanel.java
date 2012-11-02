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
package com.intellij.ui.components;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBPanel extends JPanel {
  @Nullable
  private Icon myBackgroundImage;

  public JBPanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
  }

  public JBPanel(LayoutManager layout) {
    super(layout);
  }

  public JBPanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public JBPanel() {
    super();
  }

  @Nullable
  public Icon getBackgroundImage() {
    return myBackgroundImage;
  }

  public void setBackgroundImage(@Nullable Icon backgroundImage) {
    myBackgroundImage = backgroundImage;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myBackgroundImage != null) {
      final int w = myBackgroundImage.getIconWidth();
      final int h = myBackgroundImage.getIconHeight();
      int x = 0;
      int y = 0;
      while (x < getWidth()) {
        while (y < getHeight()) {
          myBackgroundImage.paintIcon(this, g, x, y);
          y+=h;
        }
        y=0;
        x+=w;
      }
    }
  }
}
