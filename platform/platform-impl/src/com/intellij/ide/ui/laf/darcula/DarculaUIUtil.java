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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static javax.swing.SwingConstants.EAST;
import static javax.swing.SwingConstants.WEST;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaUIUtil {
  private static final Color GLOW_COLOR = new JBColor(new Color(31, 121, 212), new Color(96, 175, 255));

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_ACTIVE_ERROR_COLOR = new Color(0x80ff0f0f, true);
  private static final JBColor DEFAULT_ACTIVE_ERROR_COLOR = new JBColor(0xe53e4d, 0x8b3c3c);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_INACTIVE_ERROR_COLOR = new Color(0x80f2aaaa, true);
  private static final JBColor DEFAULT_INACTIVE_ERROR_COLOR = new JBColor(0xebbcbc, 0x725252);

  private static final Color ACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_ACTIVE_ERROR_COLOR : DEFAULT_ACTIVE_ERROR_COLOR);
  private static final Color INACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_INACTIVE_ERROR_COLOR : DEFAULT_INACTIVE_ERROR_COLOR);

  public static void paintFocusRing(Graphics g, Rectangle bounds) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, bounds);
  }

  public static void paintFocusOval(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, new Rectangle(x, y, width, height), true);
  }

  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds, Component component) {
    paintSearchFocusRing(g, bounds, component, -1);
  }

  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds, Component component, int maxArcSize) {
    int correction = UIUtil.isUnderAquaLookAndFeel() ? 30 : UIUtil.isUnderDarcula() ? 50 : 0;
    final Color[] colors = new Color[]{
      ColorUtil.toAlpha(GLOW_COLOR, 180 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 120 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 70  - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 100 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 50  - correction)
    };

    final Object oldAntialiasingValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);


    final Rectangle r = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);
    int arcSize = r.height - 1;
    if (maxArcSize>0) arcSize = Math.min(maxArcSize, arcSize);
    if (arcSize %2 == 1) arcSize--;


    g.setColor(component.getBackground());
    g.fillRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, arcSize - 4, arcSize - 4);

    g.setColor(colors[0]);
    g.drawRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, arcSize-4, arcSize-4);

    g.setColor(colors[1]);
    g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arcSize-2, arcSize-2);

    g.setColor(colors[2]);
    g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arcSize, arcSize);


    g.setColor(colors[3]);
    g.drawRoundRect(r.x+3, r.y+3, r.width - 7, r.height - 7, arcSize-6, arcSize-6);

    g.setColor(colors[4]);
    g.drawRoundRect(r.x+4, r.y+4, r.width - 9, r.height - 9, arcSize-8, arcSize-8);

    // restore rendering hints
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

  public static void paintErrorBorder(Graphics2D g, int width, int height, boolean hasFocus) {
    int lw = JBUI.scale(UIUtil.isUnderDefaultMacTheme() ? 3 : 2);
    Shape shape = new RoundRectangle2D.Double(lw, lw, width - lw * 2, height - lw * 2, lw, lw);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    g.setPaint(hasFocus ? ACTIVE_ERROR_COLOR : INACTIVE_ERROR_COLOR);
    g.setStroke(new OuterStroke(lw));
    g.draw(shape);
  }

  public static boolean isCurrentEventShiftDownEvent() {
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    return (event instanceof KeyEvent && ((KeyEvent)event).isShiftDown());
  }

  /**
   * @see javax.swing.plaf.basic.BasicTextUI#getNextVisualPositionFrom(JTextComponent, int, Position.Bias, int, Position.Bias[])
   * @return -1 if visual position shouldn't be patched, otherwise selection start or selection end
   */
  public static int getPatchedNextVisualPositionFrom(JTextComponent t, int pos, int direction) {
    if (!isCurrentEventShiftDownEvent()) {
      if (direction == WEST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionEnd() == pos) {
        return t.getSelectionStart();
      }
      if (direction == EAST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionStart() == pos) {
        return t.getSelectionEnd();
      }
    }
    return -1;
  }

  private static class OuterStroke implements Stroke {
    private final BasicStroke stroke;

    private OuterStroke(float width) {
      stroke = new BasicStroke(width);
    }

    public Shape createStrokedShape(Shape s) {
      float lw = stroke.getLineWidth();
      float delta = lw / 2f;

      if (s instanceof Rectangle2D) {
        Rectangle2D rs = (Rectangle2D) s;
        return stroke.createStrokedShape(
          new Rectangle2D.Double(rs.getX() - delta,
                                 rs.getY() - delta,
                                 rs.getWidth() + lw,
                                 rs.getHeight() + lw));
      } else if (s instanceof RoundRectangle2D) {
        RoundRectangle2D rrs = (RoundRectangle2D) s;
        return stroke.createStrokedShape(
          new RoundRectangle2D.Double(rrs.getX() - delta,
                                      rrs.getY() - delta,
                                      rrs.getWidth() + lw,
                                      rrs.getHeight() + lw,
                                      rrs.getArcWidth() + lw,
                                      rrs.getArcHeight() + lw));
      } else {
        throw new UnsupportedOperationException("Shape is not supported");
      }
    }
  }
}
