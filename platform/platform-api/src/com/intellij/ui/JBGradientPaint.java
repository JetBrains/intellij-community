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
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

public class JBGradientPaint extends GradientPaint {
  public JBGradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2) {
    super(x1, y1, color1, x2, y2, color2);
  }

  public JBGradientPaint(Point2D pt1, Color color1, Point2D pt2, Color color2) {
    super(pt1, color1, pt2, color2);
  }

  @Override
  public PaintContext createContext(ColorModel cm,
                                    Rectangle deviceBounds,
                                    Rectangle2D userBounds,
                                    AffineTransform xform,
                                    RenderingHints hints) {
    return new JBGradientPaintContext(cm, getPoint1(), getPoint2(), xform, getColor1(), getColor2());
  }

  public static void main(String[] args) {
    final Color c1 = new Color(0, 155, 0);
    final Color c2 = new Color(0, 111, 0);
    final int size = 500;
    JFrame f = new JFrame("JBGradientPaint");
    JPanel contentPane = new JPanel(new GridLayout(1, 2, 1, 1));
    f.setContentPane(contentPane);
    JPanel leftPanel = new JPanel(){
      @Override
      public void paint(Graphics g) {
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, c1, size/2, size, c2));
        g.fillRect(0, 0, size, size);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(size, size);
      }
    };
    JPanel rightPanel = new JPanel(){
      @Override
      public void paint(Graphics g) {
        ((Graphics2D)g).setPaint(new JBGradientPaint(0, 0, c1, size/2, size, c2));
        g.fillRect(0, 0, size, size);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(size, size);
      }
    };
    leftPanel.setOpaque(true);
    rightPanel.setOpaque(true);
    contentPane.add(leftPanel);
    contentPane.add(rightPanel);
    f.pack();
    f.setLocationRelativeTo(null);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    f.setVisible(true);
  }
}
