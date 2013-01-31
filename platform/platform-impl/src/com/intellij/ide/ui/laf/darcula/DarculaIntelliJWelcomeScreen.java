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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaIntelliJWelcomeScreen implements WelcomeScreen {
  private final static Icon logo = IconLoader.getIcon("/idea_logo_welcome.png");

  @Override
  public JComponent getWelcomePanel() {
    final JBPanel root = new JBPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(940, 580);
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final int w = getWidth();
        final int iw = logo.getIconWidth();
        ((Graphics2D)g).setPaint(new RadialGradientPaint(w / 2, 80, Math.max(getWidth()/2, getHeight()), new float[]{0.0f, 1.0f}, new Color[]{Gray._255.withAlpha(70), Gray._255.withAlpha(0)}));
        ((Graphics2D)g).fillRect(0, 0, getWidth(), getHeight());
        logo.paintIcon(this, g, w / 2 - iw /2, 30);
        final Font tahoma = new Font("Tahoma", Font.BOLD, 22);
        final int stringWidth = SwingUtilities2.stringWidth(this, getFontMetrics(tahoma), "Develop with Pleasure");
        final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        g.setColor(Gray._255.withAlpha(40));
        g.setFont(tahoma);
        ((Graphics2D)g).drawString("Develop with Pleasure", (w - stringWidth) / 2, getHeight() - 19);
        g.setColor(UIUtil.getPanelBackground());
        ((Graphics2D)g).drawString("Develop with Pleasure", (w - stringWidth)/2, getHeight() - 20);
        config.restore();

      }
    };
    root.setBackgroundImage(IconLoader.getIcon("/frame_background.png"));
    root.setOpaque(false);
    root.setBorder(new LineBorder(Gray._128));
    root.add(new DarculaWelcomeScreenForm(this).getComponent(), BorderLayout.CENTER);
    return root;
  }

  @Override
  public void setupFrame(JFrame frame) {
  }


  @Override
  public void dispose() {
  }
}
