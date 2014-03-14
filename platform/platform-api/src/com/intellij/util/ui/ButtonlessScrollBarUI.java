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
package com.intellij.util.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class ButtonlessScrollBarUI extends BasicScrollBarUI {

  public static JBColor getGradientLightColor() {
    return new JBColor(Gray._251, Gray._95);
  }

  public static JBColor getGradientDarkColor() {
    return new JBColor(Gray._215, Gray._80);
  }

  private static JBColor getGradientThumbBorderColor() {
    return new JBColor(Gray._201, Gray._85);
  }

  public static JBColor getTrackBackground() {
    return new JBColor(LightColors.SLIGHTLY_GRAY, UIUtil.getListBackground());
  }

  public static JBColor getTrackBorderColor() {
    return new JBColor(Gray._230, UIUtil.getListBackground());
  }

  private static final BasicStroke BORDER_STROKE = new BasicStroke();

  private static int getAnimationColorShift() {
    return UIUtil.isUnderDarcula() ? 20 : 40;
  }

  private final AdjustmentListener myAdjustmentListener;
  private final MouseMotionAdapter myMouseMotionListener;
  private final MouseAdapter myMouseListener;

  public static final int DELAY_FRAMES = 4;
  public static final int FRAMES_COUNT = 10 + DELAY_FRAMES;

  private Animator myThumbFadeAnimator;
  private int myThumbFadeColorShift = 0;
 
  private boolean myMouseIsOverThumb = false;
  private boolean myMouseOverScrollbar;
  private boolean myMouseOverScrollbarStarted;

  private Animator myMacScrollbarFadeAnimator;
  private double myMacScrollbarFadeLevel = 0;
  private boolean isMacScrollbarHidden;
 

  protected ButtonlessScrollBarUI() {
    myAdjustmentListener = new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        resetThumbAnimator();
        resetMacScrollbarFadeout();
      }
    };

    myMouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        boolean inside = isOverThumb(e.getPoint());
        if (inside != myMouseIsOverThumb) {
          myMouseIsOverThumb = inside;
          resetThumbAnimator();
        }
      }
    };

    myMouseListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myMouseOverScrollbar = true;
        myMouseOverScrollbarStarted = true;
        resetMacScrollbarFadeout();
      }
      
      @Override
      public void mouseExited(MouseEvent e) {
        if (myMouseIsOverThumb) {
          myMouseIsOverThumb = false;
          resetThumbAnimator();
        }

        myMouseOverScrollbar = false;
        resetMacScrollbarFadeout();
      }
    };
  }

  protected static boolean isMacScrollbar() {
    return UIUtil.isUnderNativeMacLookAndFeel();
  }

  public boolean alwaysShowTrack() {
    return !isMacScrollbar();
  }

  @Override
  public void layoutContainer(Container scrollbarContainer) {
    try {
      super.layoutContainer(scrollbarContainer);
    } catch (NullPointerException ignore) {
      //installUI is not performed yet or uninstallUI has set almost every field to null. Just ignore it //IDEA-89674
    }
  }

  @Override
  protected ModelListener createModelListener() {
    return new ModelListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (scrollbar != null) {
          super.stateChanged(e);
        }
      }
    };
  }

  public int getDecrementButtonHeight() {
    return decrButton.getHeight();
  }
  public int getIncrementButtonHeight() {
    return incrButton.getHeight();
  }

  private void resetThumbAnimator() {
    myThumbFadeAnimator.reset();
    if (scrollbar != null && scrollbar.getValueIsAdjusting() || myMouseIsOverThumb || Registry.is("ui.no.bangs.and.whistles")) {
      myThumbFadeAnimator.suspend();
      myThumbFadeColorShift = getAnimationColorShift();
    }
    else {
      myThumbFadeAnimator.resume();
    }
  }

  private void resetMacScrollbarFadeout() {
    if (!isMacScrollbar()) return;
    
    myMacScrollbarFadeAnimator.suspend();
    myMacScrollbarFadeAnimator.reset();
    isMacScrollbarHidden = false;
    myMacScrollbarFadeLevel = 0;

    if (scrollbar != null) {
      scrollbar.repaint();

      if (!myMouseOverScrollbar && !scrollbar.getValueIsAdjusting()) {
        myMacScrollbarFadeAnimator.resume();
      }
    }
  }

  public static BasicScrollBarUI createNormal() {
    return new ButtonlessScrollBarUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    scrollbar.setFocusable(false);
    scrollbar.setOpaque(alwaysShowTrack());
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
    if (myThumbFadeAnimator == null || myThumbFadeAnimator.isDisposed()) {
      myThumbFadeAnimator = createAnimator();
    }
    
    if (myMacScrollbarFadeAnimator == null || myMacScrollbarFadeAnimator.isDisposed()) {
      myMacScrollbarFadeAnimator = new Animator("Mac scrollbar fade animator", 30, 800, false) {
        @Override
        protected void paintCycleEnd() {
          isMacScrollbarHidden = true;
          myMouseOverScrollbar = false;
          myMouseOverScrollbarStarted = false;

          if (scrollbar != null) scrollbar.repaint();
        }

        @Override
        public void paintNow(int frame, int totalFrames, int cycle) {
          int delay = (int)(totalFrames / 1.5);
          int frameAfterDelay = frame - delay;
          
          if (frameAfterDelay > 0) {
            myMacScrollbarFadeLevel = frameAfterDelay / (float)(totalFrames - delay);
            if (scrollbar != null) scrollbar.repaint();
          }
        }
      };
    } 

    super.installListeners();
    scrollbar.addAdjustmentListener(myAdjustmentListener);
    scrollbar.addMouseListener(myMouseListener);
    scrollbar.addMouseMotionListener(myMouseMotionListener);
    
    resetMacScrollbarFadeout();
  }

  private Animator createAnimator() {
    return new Animator("Adjustment fadeout", FRAMES_COUNT, FRAMES_COUNT * 50, false) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        myThumbFadeColorShift = getAnimationColorShift();
        if (frame > DELAY_FRAMES) {
          myThumbFadeColorShift *= 1 - ((double)(frame - DELAY_FRAMES)) / ((double)(totalFrames - DELAY_FRAMES));
        }

        if (scrollbar != null) {
          scrollbar.repaint(((ButtonlessScrollBarUI)scrollbar.getUI()).getThumbBounds());
        }
      }
    };
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
    Disposer.dispose(myMacScrollbarFadeAnimator);
    
    scrollbar.removeAdjustmentListener(myAdjustmentListener);
    Disposer.dispose(myThumbFadeAnimator);
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    final int thickness = getThickness();
    return isVertical() ? new Dimension(thickness, thickness * 2) : new Dimension(thickness * 2, thickness);
  }

  protected int getThickness() {
    return isMacScrollbar() ? 15 : 13;
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
  public boolean contains(JComponent c, int x, int y) {
    if (!alwaysShowTrack() && isMacScrollbarHidden) return false;  
    return super.contains(c, x, y);
  }


  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    if (alwaysShowTrack() || myMouseOverScrollbarStarted) {
      doPaintTrack(g, c, trackBounds);
    }
  }

  protected void doPaintTrack(Graphics g, JComponent c, Rectangle bounds) {
    if (isMacScrollbar()) {
      boolean vertical = isVertical();
      
      final Paint paint;
      final Color start = adjustColor(UIUtil.getSlightlyDarkerColor(getTrackBackground()));
      final Color end = adjustColor(getTrackBackground().brighter());
  
      if (vertical) {
        paint = UIUtil.getGradientPaint(1, 0, start, bounds.width + 1, 0, end);
      }
      else {
        paint = UIUtil.getGradientPaint(0, 1, start, 0, bounds.height + 1, end);
      }

      Graphics2D g2d = (Graphics2D)g;
      g2d.setPaint(paint);
      g2d.fillRect(0, 0, bounds.width, bounds.height);

      g.setColor(adjustColor(start.darker()));
    } else {
      g.setColor(getTrackBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      g.setColor(getTrackBorderColor());
    }

    if (isVertical()) {
      g.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
    }
    else {
      g.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
    }
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (isMacScrollbarHidden) return;
    doPaintThumb(g, thumbBounds);
  }

  private void doPaintThumb(Graphics g, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }

    if (isMacScrollbar()) {
      paintMacThumb(g, thumbBounds);
    }
    else {
      g.translate(thumbBounds.x, thumbBounds.y);
      paintMaxiThumb((Graphics2D)g, thumbBounds);
      g.translate(-thumbBounds.x, -thumbBounds.y);
    }
  }

  private void paintMacThumb(Graphics g, Rectangle thumbBounds) {
    boolean vertical = isVertical();
    int baseThumbSize = vertical ? thumbBounds.width : thumbBounds.height;
    int borderSize = 2;
    int thumbSize = myMouseOverScrollbarStarted ? baseThumbSize - borderSize * 2 : baseThumbSize / 2;

    int x = thumbBounds.x;
    int y = thumbBounds.y;

    if (vertical) {
      x += thumbBounds.width - thumbSize - borderSize;
    }
    else {
      y += thumbBounds.height - thumbSize - borderSize;
    }

    int width = vertical ? thumbSize : thumbBounds.width - (borderSize * 2);
    width = Math.max(width, thumbSize);

    int height = vertical ? thumbBounds.height - (borderSize * 2) : thumbSize;
    height = Math.max(height, thumbSize);

    Graphics2D g2d = (Graphics2D)g;
    RenderingHints oldHints = g2d.getRenderingHints();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);

    g2d.setColor(adjustColor(JBColor.BLACK));
    g2d.fillRoundRect(x, y, width, height, thumbSize, thumbSize);
    
    g2d.setRenderingHints(oldHints);
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

    final Paint paint;
    final Color start = adjustColor(getGradientLightColor());
    final Color end = adjustColor(getGradientDarkColor());

    if (vertical) {
      paint = UIUtil.getGradientPaint(1, 0, start, w + 1, 0, end);
    }
    else {
      paint = UIUtil.getGradientPaint(0, 1, start, 0, h + 1, end);
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
    if (isMacScrollbar()) {
      int alpha = (int)((myMouseOverScrollbarStarted ? 140 : 120) * (1 - myMacScrollbarFadeLevel));
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
    else {
      if (myThumbFadeColorShift == 0) return c;
      final int sign = UIUtil.isUnderDarcula() ? -1 : 1;
      return Gray.get(Math.max(0, Math.min(255, c.getRed() - sign * myThumbFadeColorShift)));
    }
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
