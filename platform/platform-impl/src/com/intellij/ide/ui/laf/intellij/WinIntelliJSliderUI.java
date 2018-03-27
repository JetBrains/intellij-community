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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public class WinIntelliJSliderUI extends BasicSliderUI {
  private static final String HOVER_PROPERTY = "JSlider.mouseHover";
  private static final String PRESSED_PROPERTY = "JSlider.mousePressed";

  private WinIntelliJSliderUI() {
    super(null); // super constructor is no-op. JSlider is assigned in installUI
  }

  private MouseListener mouseListener;
  private Color         ticksColor;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent b)    {
    return new WinIntelliJSliderUI();
  }

  @Override protected void installListeners(JSlider slider) {
    super.installListeners(slider);

    mouseListener = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) {
        setProperty(e, PRESSED_PROPERTY, Boolean.TRUE);
      }

      @Override public void mouseReleased(MouseEvent e) {
        setProperty(e, PRESSED_PROPERTY, Boolean.FALSE);
      }

      @Override public void mouseEntered(MouseEvent e) {
        setProperty(e, HOVER_PROPERTY, Boolean.TRUE);
      }

      @Override public void mouseExited(MouseEvent e) {
        setProperty(e, HOVER_PROPERTY, Boolean.FALSE);
      }

      private void setProperty(MouseEvent e, String property, Boolean value) {
        JComponent c = (JComponent)e.getComponent();
        c.putClientProperty(property, value);
        c.repaint();
      }
    };

    slider.addMouseListener(mouseListener);
  }

  @Override protected void uninstallListeners( JSlider slider ) {
    super.uninstallListeners(slider);
    if (mouseListener != null) {
      slider.removeMouseListener(mouseListener);
    }
  }

  @Override public void paintTrack(Graphics g)  {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle2D coloredTrack, scaleRect;
      int tw = JBUI.scale(1);
      if (slider.getOrientation() == SwingConstants.HORIZONTAL) {
        coloredTrack = new Rectangle2D.Double(trackRect.x - thumbRect.width/2,
                                       trackRect.y + trackRect.height - JBUI.scale(10),
                                       thumbRect.x - trackRect.x + thumbRect.width,
                                       tw);

        scaleRect = new Rectangle2D.Double(thumbRect.x + thumbRect.width/2,
                                           trackRect.y + trackRect.height - JBUI.scale(10),
                                           trackRect.x + trackRect.width - thumbRect.x - tw,
                                           tw);
      } else {
        coloredTrack = new Rectangle2D.Double(trackRect.x + thumbRect.width - JBUI.scale(10),
                                       trackRect.y - thumbRect.height/2,
                                       tw,
                                       thumbRect.y - trackRect.y + thumbRect.height);

        scaleRect = new Rectangle2D.Double(trackRect.x + thumbRect.width - JBUI.scale(10),
                                           thumbRect.y + thumbRect.height/2,
                                           tw,
                                           trackRect.y + trackRect.height - thumbRect.y - tw);
      }

      g2.setColor(getSliderColor());
      g2.fill(coloredTrack);

      Color scaleColor = UIManager.getColor(slider.isEnabled() ? "Button.intellij.native.borderColor" : "Slider.disabledForeground");
      g2.setColor(scaleColor);
      g2.fill(scaleRect);
    } finally {
      g2.dispose();
    }
  }

  @Override protected void calculateTrackRect() {
    int centerSpacing; // used to center sliders added using BorderLayout.CENTER (bug 4275631)
    if (slider.getOrientation() == SwingConstants.HORIZONTAL) {
      centerSpacing = thumbRect.height;
      if (slider.getPaintLabels()) centerSpacing += getHeightOfTallestLabel();

      trackRect.setBounds(contentRect.x + trackBuffer,
                          contentRect.y + (contentRect.height - centerSpacing)/2,
                          contentRect.width - (trackBuffer * 2),
                          thumbRect.height);
    } else {
      centerSpacing = thumbRect.width;
      int offset = JBUI.scale(6);
      if (slider.getComponentOrientation().isLeftToRight()) {
        if (slider.getPaintLabels()) {
          centerSpacing += getWidthOfWidestLabel();
          offset = -offset;
        }
      } else {
        if (slider.getPaintLabels()) {
          centerSpacing -= getWidthOfWidestLabel();
        }
      }
      trackRect.setBounds(contentRect.x + (contentRect.width - centerSpacing)/2 + offset,
                          contentRect.y + trackBuffer,
                          thumbRect.width,
                          contentRect.height - (trackBuffer * 2));
    }
  }

  @Override protected void calculateTickRect() {
    super.calculateTickRect();
    if ( slider.getOrientation() == SwingConstants.HORIZONTAL ) {
      tickRect.y -= JBUI.scale(5);
    } else {
      if (slider.getComponentOrientation().isLeftToRight()) {
        tickRect.x -= JBUI.scale(5);
      } else {
        tickRect.x = trackRect.x + JBUI.scale(5);
      }
    }
  }

  @Override protected void calculateLabelRect() {
    super.calculateLabelRect();
    if (slider.getPaintLabels()) {
      if (slider.getOrientation() == SwingConstants.VERTICAL) {
        int distance = JBUI.scale(11); // Count tickRect
        labelRect.x += slider.getComponentOrientation().isLeftToRight() ? distance : -distance;
      } else {
        labelRect.y += JBUI.scale(6);
      }
    }
  }

  @Override protected Dimension getThumbSize() {
    return slider.getOrientation() == SwingConstants.VERTICAL ? new JBDimension(22, 8) : new JBDimension(8, 22);
  }

  @Override public void paintThumb(Graphics g) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(thumbRect.x, thumbRect.y);

      Path2D thumb = new Path2D.Float(Path2D.WIND_NON_ZERO);

      if (slider.getOrientation() == SwingConstants.VERTICAL) {
        if (slider.getComponentOrientation().isLeftToRight()) {
          thumb.moveTo(0, 0);
          thumb.lineTo(thumbRect.width - JBUI.scale(4), 0);
          thumb.lineTo(thumbRect.width, thumbRect.height / 2);
          thumb.lineTo(thumbRect.width - JBUI.scale(4), thumbRect.height);
          thumb.lineTo(0, thumbRect.height);
        } else {
          thumb.moveTo(thumbRect.width, 0);
          thumb.lineTo(thumbRect.width, thumbRect.height);
          thumb.lineTo(JBUI.scale(4), thumbRect.height);
          thumb.lineTo(0, thumbRect.height/2);
          thumb.lineTo(JBUI.scale(4), 0);
        }
      } else {
        thumb.moveTo(0, 0);
        thumb.lineTo(thumbRect.width, 0);
        thumb.lineTo(thumbRect.width, thumbRect.height - JBUI.scale(4));
        thumb.lineTo(thumbRect.width / 2, thumbRect.height);
        thumb.lineTo(0, thumbRect.height - JBUI.scale(4));
      }
      thumb.closePath();

      g2.setColor(getSliderColor());
      g2.fill(thumb);
    } finally {
      g2.dispose();
    }
  }

  @Override protected int getTickLength() {
    return JBUI.scale(4);
  }

  @Override protected void paintMinorTickForHorizSlider( Graphics g, Rectangle tickBounds, int x ) {
    Rectangle2D tick = new Rectangle2D.Double(x - 0.5, 0, JBUI.scale(1), tickBounds.height);
    paintSliderTick(g, tick);
  }

  @Override protected void paintMajorTickForHorizSlider( Graphics g, Rectangle tickBounds, int x ) {
    paintMinorTickForHorizSlider(g, tickBounds, x);
  }

  @Override protected void paintMinorTickForVertSlider( Graphics g, Rectangle tickBounds, int y ) {
    Rectangle2D tick = new Rectangle2D.Double(0, y - 0.5, tickBounds.width, JBUI.scale(1));
    paintSliderTick(g, tick);
  }

  @Override protected void paintMajorTickForVertSlider( Graphics g, Rectangle tickBounds, int y ) {
    paintMinorTickForVertSlider(g, tickBounds, y);
  }

  private void paintSliderTick(Graphics g, Rectangle2D shape) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(ticksColor);
      g2.fill(shape);
    } finally {
      g2.dispose();
    }
  }

  @Override public void paintTicks(Graphics g)  {
    ticksColor = UIManager.getColor(slider.isEnabled() ? "Button.intellij.native.borderColor" : "Slider.disabledForeground");
    super.paintTicks(g);
  }

  @Override public void paintFocus(Graphics g) {}

  private Color getSliderColor() {
    if (slider.isEnabled()) {
      if (slider.getClientProperty(PRESSED_PROPERTY) == Boolean.TRUE) {
        return UIManager.getColor("Slider.pressedColor");
      } else if (slider.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE || slider.hasFocus()) {
        return UIManager.getColor("Slider.focusedColor");
      } else {
        return UIManager.getColor("Slider.foreground");
      }
    } else {
      return UIManager.getColor("Slider.disabledForeground");
    }
  }
}
