/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextBorder extends DarculaTextBorder {
  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3, 6).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g2d, int x, int y, int width, int height) {
    Graphics2D g = (Graphics2D)g2d;
    if (c.hasFocus()) {
      MacIntelliJBorderPainter.paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
    }
    g.setColor(UIUtil.isRetina() ? Gray.xF0 : Gray.xB1);
    g.drawRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
    if (UIUtil.isRetina()) {
      Graphics2D gr = (Graphics2D)g.create(2, 2, c.getWidth() - 4, c.getHeight() - 4);
      gr.scale(0.5, 0.5);
      gr.setColor(Gray.xB1);
      gr.drawRect(1, 1, gr.getClipBounds().width - 3, gr.getClipBounds().height - 3);
      gr.scale(2, 2);
      gr.dispose();
    }
  }
}
