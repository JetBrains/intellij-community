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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBInsets;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextBorder implements Border, UIResource {
  private JBInsets myInsets = new JBInsets(4, 7, 4, 7);

  @Override
  public JBInsets getBorderInsets(Component c) {
    return myInsets;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    Graphics2D g = ((Graphics2D)g2);
    g.setColor(ColorUtil.fromHex("737373"));
    int cX = myInsets.right;
    int cY = myInsets.top;
    int cW = width - myInsets.width();
    int cH = height - myInsets.height();
    final GraphicsConfig config = new GraphicsConfig(g);
    g.translate(x, y);

    if (c.hasFocus()) {
      int sysOffX = SystemInfo.isMac ? 0 : 1;
      int sysOffY = SystemInfo.isMac ? 0 : -1;
      DarculaUIUtil.paintFocusRing(g, 2, 2, width-4, height-4);

      //g.setColor(DarculaUIUtil.GLOW_COLOR.darker().darker());
      //g.drawRect(1, 1, width - 2, height - 2);
      //g.drawRect(2, 2, width-4, height-4);
      //g.setColor(ColorUtil.toAlpha(DarculaUIUtil.GLOW_COLOR, 70));
      //g.drawRoundRect(0, 0, width, height, 5, 5);
      //g.setColor(ColorUtil.toAlpha(DarculaUIUtil.GLOW_COLOR, 80));
      //g.drawRoundRect(1, 1, width - 2, height-2, 5, 5);
      //g.setColor(ColorUtil.toAlpha(DarculaUIUtil.GLOW_COLOR, 120));
      //g.drawRoundRect(2, 2, width - 4, height - 4, 5, 5);
      //g.setColor(ColorUtil.toAlpha(DarculaUIUtil.GLOW_COLOR, 140));
      //g.drawRoundRect(3, 2, width - 6, height - 4, 5, 5);
    } else {
      //g.fillRect(2, 1, cX-2, height - 2); //left
      //g.fillRect(cX + cW , 1, width-cW - cX, height - 2); //right
      //g.fillRect(1, 1, width - 2, cY); //top
      //g.fillRect(1, cY + cH, width - 2, height - 2); //bottom
      g.setColor(c.isEnabled() && ((JTextComponent)c).isEditable() ? new Color(0x939393) : new Color(0x535353));
      g.drawRect(1, 1, width - 2, height - 2);
    }
    g.translate(-x, -y);
    config.restore();
  }
}
