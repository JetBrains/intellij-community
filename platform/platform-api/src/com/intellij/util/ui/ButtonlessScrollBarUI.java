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
package com.intellij.util.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.Gray;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class ButtonlessScrollBarUI extends BasicScrollBarUI {
  private static final Color GRADIENT_LIGHT = Gray._251;
  private static final Color GRADIENT_DARK = Gray._215;
  private static final Color GRADIENT_THUMB_BORDER = Gray._201;
  private static final Color TRACK_BACKGROUND = LightColors.SLIGHTLY_GRAY;
  private static final Color TRACK_BORDER = Gray._230;
  private static final Color GRADIENT_LIGHT_DARK_VARIANT = GRADIENT_LIGHT.darker().darker();
  private static final Color GRADIENT_DARK_DARK_VARIANT = GRADIENT_DARK.darker().darker();
  private static final Color GRADIENT_THUMB_BORDER_DARK_VARIANT = GRADIENT_THUMB_BORDER.darker().darker();
  private static final Color TRACK_BACKGROUND_DARK_VARIANT = TRACK_BACKGROUND.darker().darker();
  private static final Color TRACK_BORDER_DARK_VARIANT = TRACK_BORDER.darker().darker();

  private static final BasicStroke BORDER_STROKE = new BasicStroke();

  private final AdjustmentListener myAdjustmentListener;
  private final MouseMotionAdapter myMouseMotionListener;
  private final MouseAdapter myMouseListener;

  private final Animator myAnimator;

  private int myAnimationColorShift = 0;
  private boolean myMouseIsOverThumb = false;

  protected ButtonlessScrollBarUI() {
    myAdjustmentListener = new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        resetAnimator();
      }
    };

    final int delayFrames = 4;
    final int framesCount = 10 + delayFrames;
    myAnimator = new Animator("Adjustment fadeout", framesCount, framesCount * 50, false) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        myAnimationColorShift = 40;
        if (frame > delayFrames) {
          myAnimationColorShift *= 1 - ((double)(frame - delayFrames)) / ((double)(totalFrames - delayFrames));
        }

        if (scrollbar != null) {
          scrollbar.repaint(((ButtonlessScrollBarUI)scrollbar.getUI()).getThumbBounds());
        }
      }
    };

    myMouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        boolean inside = isOverThumb(e.getPoint());
        if (inside != myMouseIsOverThumb) {
          myMouseIsOverThumb = inside;
          resetAnimator();
        }
      }
    };

    myMouseListener = new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (myMouseIsOverThumb) {
          myMouseIsOverThumb = false;
          resetAnimator();
        }
      }
    };
  }

  @Override
  public void layoutContainer(Container scrollbarContainer) {
    try {
      super.layoutContainer(scrollbarContainer);
    } catch (NullPointerException ignore) {
      //installUI is not performed yet or uninstallUI has set almost every field to null. Just ignore it //IDEA-89674
    }
  }

  public static Color getGradientLightColor() {
    return UIUtil.isUnderDarcula() ? GRADIENT_LIGHT_DARK_VARIANT : GRADIENT_LIGHT;
  }

  public static Color getGradientDarkColor() {
    return UIUtil.isUnderDarcula() ? GRADIENT_DARK_DARK_VARIANT : GRADIENT_DARK;
  }

  public static Color getGradientThumbBorderColor() {
    return UIUtil.isUnderDarcula() ? GRADIENT_THUMB_BORDER_DARK_VARIANT : GRADIENT_THUMB_BORDER;
  }

  public static Color getTrackBackground() {
    return UIUtil.isUnderDarcula() ? UIUtil.getControlColor() : TRACK_BACKGROUND;
  }

  public static Color getTrackBorderColor() {
    return UIUtil.isUnderDarcula() ? UIUtil.getControlColor() : TRACK_BORDER;
  }

  public int getDecrButtonHeight() {
    return decrButton.getHeight();
  }
  public int getIncrButtonHeight() {
    return incrButton.getHeight();
  }

  private void resetAnimator() {
    myAnimator.reset();
    if (scrollbar != null && scrollbar.getValueIsAdjusting() || myMouseIsOverThumb) {
      myAnimator.suspend();
      myAnimationColorShift = 40;
    }
    else {
      myAnimator.resume();
    }
  }

  public static BasicScrollBarUI createNormal() {
    return new ButtonlessScrollBarUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    scrollbar.setFocusable(false);
  }

  @Override
  protected void installDefaults() {
    final int incGap = UIManager.getInt("ScrollBar.incrementButtonGap");
    final int decGap = UIManager.getInt("ScrollBar.decrementButtonGap");
    try {
      UIManager.put("ScrollBar.incrementButtonGap", 0);
      UIManager.put("ScrollBar.decrementButtonGap", 0);
      super.installDefaults();
    }
    finally {
      UIManager.put("ScrollBar.incrementButtonGap", incGap);
      UIManager.put("ScrollBar.decrementButtonGap", decGap);
    }
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    scrollbar.addAdjustmentListener(myAdjustmentListener);
    scrollbar.addMouseListener(myMouseListener);
    scrollbar.addMouseMotionListener(myMouseMotionListener);
  }

  private boolean isOverThumb(Point p) {
    final Rectangle bounds = getThumbBounds();
    return bounds != null && bounds.contains(p);
  }

  @Override
  public Rectangle getThumbBounds() {
    return super.getThumbBounds();
  }

  @Override
  protected void uninstallListeners() {
    if (scrollTimer != null) {
      // it is already called otherwise
      super.uninstallListeners();
    }
    scrollbar.removeAdjustmentListener(myAdjustmentListener);
    Disposer.dispose(myAnimator);
  }

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
    g.setColor(getTrackBackground());
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

    g.setColor(getTrackBorderColor());
    if (isVertical()) {
      g.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
    }
    else {
      g.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
    }
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    final int thickness = getThickness();
    return isVertical() ? new Dimension(thickness, thickness * 2) : new Dimension(thickness * 2, thickness);
  }

  protected int getThickness() {
    return 13;
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    int thickness = getThickness();
    return new Dimension(thickness, thickness);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getMaximumSize(c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getMaximumSize(c);
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }

    g.translate(thumbBounds.x, thumbBounds.y);
    paintMaxiThumb((Graphics2D)g, thumbBounds);
    g.translate(-thumbBounds.x, -thumbBounds.y);
  }

  private void paintMaxiThumb(Graphics2D g, Rectangle thumbBounds) {
    final boolean vertical = isVertical();
    int hGap = vertical ? 2 : 1;
    int vGap = vertical ? 1 : 2;

    int w = adjustThumbWidth(thumbBounds.width - hGap * 2);
    int h = thumbBounds.height - vGap * 2;

    // leave one pixel between thumb and right or bottom edge
    if (vertical) {
      h -= 1;
    }
    else {
      w -= 1;
    }

    final GradientPaint paint;
    final Color start = adjustColor(getGradientLightColor());
    final Color end = adjustColor(getGradientDarkColor());

    if (vertical) {
      paint = new GradientPaint(1, 0, start, w + 1, 0, end);
    }
    else {
      paint = new GradientPaint(0, 1, start, 0, h + 1, end);
    }

    g.setPaint(paint);
    g.fillRect(hGap + 1, vGap + 1, w - 1, h - 1);

    final Stroke stroke = g.getStroke();
    g.setStroke(BORDER_STROKE);
    g.setColor(getGradientThumbBorderColor());
    g.drawRoundRect(hGap, vGap, w, h, 3, 3);
    g.setStroke(stroke);
  }

  @Override
  public boolean getSupportsAbsolutePositioning() {
    return true;
  }

  protected int adjustThumbWidth(int width) {
    return width;
  }

  protected Color adjustColor(Color c) {
    if (myAnimationColorShift == 0) return c;
    return Gray.get(c.getRed() - myAnimationColorShift);
  }

  private boolean isVertical() {
    return scrollbar.getOrientation() == Adjustable.VERTICAL;
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new EmptyButton();
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new EmptyButton();
  }

  private static class EmptyButton extends JButton {
    private EmptyButton() {
      setFocusable(false);
      setRequestFocusEnabled(false);
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(0, 0);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMaximumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return getMaximumSize();
    }
  }
}
