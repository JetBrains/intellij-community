/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaProgressBarUI extends BasicProgressBarUI {

  private static final Color REMAINDER_COLOR = new JBColor(Gray.xC4, Gray.x69);
  private static final Color FINISHED_COLOR = new JBColor(Gray.x80, Gray.xA0);

  private static final Color ERROR_COLOR = new JBColor(new Color(0xd80000), new Color(0xff4053));
  private static final Color SUCCESS_COLOR = new JBColor(new Color(0x34b171), new Color(0x008f50));

  private static final Color START_COLOR = new JBColor(Gray.xC4, Gray.x69);
  private static final Color END_COLOR = new JBColor(Gray.x80, Gray.x83);

  private static final Color ERROR_START_COLOR = new JBColor(new Color(0xFB8F89), new Color(0xf4a2a0));
  private static final Color ERROR_END_COLOR = ERROR_COLOR;
  private static final Color SUCCESS_START_COLOR = new JBColor(new Color(0x7EE8A5), new Color(0x5dc48f));
  private static final Color SUCCESS_END_COLOR = SUCCESS_COLOR;

  private static final int STEP = 6;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaProgressBarUI();
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    UIManager.put("ProgressBar.repaintInterval", new Integer(50));
    UIManager.put("ProgressBar.cycleTime", new Integer(800));
  }

  @Override
  protected void paintIndeterminate(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle r = new Rectangle(progressBar.getSize());
      if (c.isOpaque()) {
        g2.setColor(c.getParent().getBackground());
        g2.fill(r);
      }

      Insets i = progressBar.getInsets();
      JBInsets.removeFrom(r, i);
      int orientation = progressBar.getOrientation();

      // Detect gradient color
      Color startColor, endColor;
      String type = (String)progressBar.getClientProperty("ProgressBar.color");
      if ("error".equals(type)) {
        startColor = ERROR_START_COLOR;
        endColor = ERROR_END_COLOR;
      } else if ("success".equals(type)) {
        startColor = SUCCESS_START_COLOR;
        endColor = SUCCESS_END_COLOR;
      } else {
        startColor = getStartColor();
        endColor = getEndColor();
      }

      RoundRectangle2D shape;
      int step = JBUI.scale(STEP);
      if (orientation == SwingConstants.HORIZONTAL) {
        int pHeight = progressBar.getPreferredSize().height;
        float yOffset = r.y + (r.height - pHeight) / 2.0f;

        shape = new RoundRectangle2D.Float(r.x, yOffset, r.width, pHeight, pHeight, pHeight);

        yOffset = r.y + pHeight/2.0f;
        g2.setPaint(new GradientPaint(r.x + getAnimationIndex()*step*2, yOffset, startColor,
                                      r.x + getFrameCount()*step + getAnimationIndex()*step*2, yOffset, endColor, true));
      } else {
        int pWidth = progressBar.getPreferredSize().width;
        float xOffset = r.x + (r.width - pWidth) / 2.0f;

        shape = new RoundRectangle2D.Float(xOffset, r.y, pWidth, r.height, pWidth, pWidth);
        xOffset = r.x + pWidth/2.0f;
        g2.setPaint(new GradientPaint(xOffset, r.y + getAnimationIndex()*step*2, startColor,
                                      xOffset, r.y + getFrameCount()*step + getAnimationIndex()*step*2, endColor, true));
      }
      g2.fill(shape);

      if (progressBar.isStringPainted()) {
        if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
          paintString(g2, i.left, i.top, r.width, r.height, boxRect.x, boxRect.width);
        } else {
          paintString(g2, i.left, i.top, r.width, r.height, boxRect.y, boxRect.height);
        }
      }
    } finally {
      g2.dispose();
    }
  }

  protected Color getStartColor() {
    return START_COLOR;
  }

  protected Color getEndColor() {
    return END_COLOR;
  }
  private void paintString(Graphics g, int x, int y, int w, int h, int fillStart, int amountFull) {
    if (!(g instanceof Graphics2D)) {
      return;
    }

    Graphics2D g2 = (Graphics2D)g;
    String progressString = progressBar.getString();
    g2.setFont(progressBar.getFont());
    Point renderLocation = getStringPlacement(g2, progressString,
                                              x, y, w, h);
    Rectangle oldClip = g2.getClipBounds();

    if (progressBar.getOrientation() == SwingConstants.HORIZONTAL) {
      g2.setColor(getSelectionBackground());
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
      g2.setColor(getSelectionForeground());
      g2.clipRect(fillStart, y, amountFull, h);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
    } else { // VERTICAL
      g2.setColor(getSelectionBackground());
      AffineTransform rotate =
        AffineTransform.getRotateInstance(Math.PI/2);
      g2.setFont(progressBar.getFont().deriveFont(rotate));
      renderLocation = getStringPlacement(g2, progressString,
                                          x, y, w, h);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
      g2.setColor(getSelectionForeground());
      g2.clipRect(x, fillStart, w, amountFull);
      SwingUtilities2.drawString(progressBar, g2, progressString,
                                 renderLocation.x, renderLocation.y);
    }
    g2.setClip(oldClip);
  }

  @Override
  protected void paintDeterminate(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle r = new Rectangle(progressBar.getSize());
      if (c.isOpaque()) {
        g2.setColor(c.getParent().getBackground());
        g2.fill(r);
      }

      Insets i = progressBar.getInsets();
      JBInsets.removeFrom(r, i);
      int orientation = progressBar.getOrientation();
      int amountFull = getAmountFull(progressBar.getInsets(), r.width, r.height);

      RoundRectangle2D fullShape;
      RoundRectangle2D coloredShape;
      if (orientation == SwingConstants.HORIZONTAL) {
        int pHeight = progressBar.getPreferredSize().height;
        double yOffset = r.y + (r.height - pHeight) / 2.0;

        fullShape = new RoundRectangle2D.Double(r.x, yOffset, r.width, pHeight, pHeight, pHeight);
        coloredShape = new RoundRectangle2D.Double(r.x, yOffset, amountFull, pHeight, pHeight, pHeight);
      } else {
        int pWidth = progressBar.getPreferredSize().width;
        double xOffset = r.x + (r.width - pWidth) / 2.0;

        fullShape = new RoundRectangle2D.Double(xOffset, r.y, pWidth, r.height, pWidth, pWidth);
        coloredShape = new RoundRectangle2D.Double(xOffset, r.y, pWidth, amountFull, pWidth, pWidth);
      }
      g2.setColor(getRemainderColor());
      g2.fill(fullShape);


      String type = (String)progressBar.getClientProperty("ProgressBar.color");
      if ("error".equals(type)) {
        g2.setColor(ERROR_COLOR);
      } else if ("success".equals(type)) {
        g2.setColor(SUCCESS_COLOR);
      } else {
        g2.setColor(getFinishedColor());
      }
      g2.fill(coloredShape);

      // Deal with possible text painting
      if (progressBar.isStringPainted()) {
        paintString(g, i.left, i.top, r.width, r.height, amountFull, i);
      }
    } finally {
      g2.dispose();
    }
  }

  protected Color getRemainderColor() {
    return REMAINDER_COLOR;
  }

  protected Color getFinishedColor() {
    return FINISHED_COLOR;
  }

  @Override public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (!(c instanceof JProgressBar)) {
      return size;
    }

    boolean modeless = progressBar.getClientProperty("ProgressBar.modeless") == Boolean.TRUE;
    int orientation = ((JProgressBar)c).getOrientation();
    if (orientation == SwingConstants.HORIZONTAL) {
      size.height = JBUI.scale(modeless ? 2 : 4);
    } else {
      size.width = JBUI.scale(modeless ? 2 : 4);
    }
    return  size;
  }

  @Override
  protected int getBoxLength(int availableLength, int otherDimension) {
    return availableLength;
  }

  // TODO: remove methods. Not used anymore.
  @Deprecated
  protected volatile int offset = 0;

  @Deprecated
  protected int getPeriodLength() {
    return 0;
  }
}
