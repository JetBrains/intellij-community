/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMenuBarBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    w--;h--;
    g.setColor(UIManager.getColor("MenuBar.darcula.borderColor"));
    g.drawLine(0, h, w, h);
    h--;
    g.setColor(UIManager.getColor("MenuBar.darcula.borderShadowColor"));
    g.drawLine(0, h, w, h);
    g.translate(-x, -y);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(0, 0, 2, 0);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
