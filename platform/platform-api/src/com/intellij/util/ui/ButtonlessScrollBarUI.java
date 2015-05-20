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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Method;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class ButtonlessScrollBarUI extends BasicScrollBarUI {
  private static final Logger LOG = Logger.getInstance("#" + ButtonlessScrollBarUI.class.getName());

  protected JBColor getGradientLightColor() {
    return jbColor(Gray._251, Gray._95);
  }

  protected JBColor getGradientDarkColor() {
    return jbColor(Gray._215, Gray._80);
  }

  private JBColor getGradientThumbBorderColor() {
    return jbColor(Gray._201, Gray._85);
  }

  public static JBColor getTrackBackgroundDefault() {
    return new JBColor(LightColors.SLIGHTLY_GRAY, UIUtil.getListBackground());
  }

  public static JBColor getTrackBorderColorDefault() {
    return new JBColor(Gray._230, UIUtil.getListBackground());
  }

  private JBColor getTrackBackground() {
    return jbColor(LightColors.SLIGHTLY_GRAY, UIUtil.getListBackground());
  }
  
  private JBColor getTrackBorderColor() {
    return jbColor(Gray._230, UIUtil.getListBackground());
  }

  private static final BasicStroke BORDER_STROKE = new BasicStroke();
  
  private JBColor jbColor(final Color regular, final Color dark) {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return isDark() ? dark : regular;
      }
    });
  }

  private int getAnimationColorShift() {
    return isDark() ? 20 : 40;
  }

  private final AdjustmentListener myAdjustmentListener;
  private final MouseMotionAdapter myMouseMotionListener;
  private final MouseAdapter myMouseListener;
  private final HierarchyListener myHierarchyListener;
  private final AWTEventListener myAWTMouseListener;
  private final NSScrollerHelper.ScrollbarStyleListener myNSScrollerListener;
  private boolean myGlobalListenersAdded;

  public static final int DELAY_FRAMES = 4;
  public static final int FRAMES_COUNT = 10 + DELAY_FRAMES;

  private Animator myThumbFadeAnimator;
  private int myThumbFadeColorShift = 0;
 
  private boolean myMouseIsOverThumb = false;
  private boolean myMouseOverScrollbar;
  private double myMouseOverScrollbarExpandLevel = 0;

  private NSScrollerHelper.Style myMacScrollerStyle;
  private Animator myMouseOverScrollbarExpandAnimator;
  private Alarm myMacScrollbarFadeTimer;
  private Animator myMacScrollbarFadeAnimator;
  private double myMacScrollbarFadeLevel = 0;
  private boolean myMacScrollbarHidden;

  private ScrollbarRepaintCallback myRepaintCallback;

  protected ButtonlessScrollBarUI() {
    myAdjustmentListener = new AdjustmentListener() {
      Point oldViewportPosition = null;
      Dimension oldViewportDimension = null;

      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        JScrollPane scrollpane = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, scrollbar);
        JViewport viewport = scrollpane == null ? null : scrollpane.getViewport();

        if (viewport == null) {
          oldViewportPosition = null;
          return;
        }

        boolean vertical = isVertical();
        Point position = viewport.getViewPosition();
        // we don't take viewport's size here since it often changes on scrollbar appearance.
        // instead, we want to only react on visible area resizes
        Dimension dimension = scrollpane.getSize();

        boolean scrolled = false;
        if (oldViewportPosition != null) {
          int scrollH = position.x - oldViewportPosition.x;
          int scrollV = position.y - oldViewportPosition.y;
          scrolled = vertical && scrollH == 0 && scrollV != 0 ||
                     !vertical && scrollV == 0 && scrollH != 0;
        }
        oldViewportPosition = position;

        boolean resized = false;
        if (oldViewportDimension != null) {
          int resizedH = dimension.width - oldViewportDimension.width;
          int resizedV = dimension.height - oldViewportDimension.height;
          resized = vertical && resizedV != 0 || !vertical && resizedH != 0;
        }
        oldViewportDimension = dimension;
        
        if (scrolled) {
          // hide the opposite scrollbar when user scrolls 
          JScrollBar other = vertical ? scrollpane.getHorizontalScrollBar()
                                      : scrollpane.getVerticalScrollBar();
          ScrollBarUI otherUI = other == null ? null : other.getUI();
          if (otherUI instanceof ButtonlessScrollBarUI) {
            ((ButtonlessScrollBarUI)otherUI).startMacScrollbarFadeout(true);
          }

          restart();
        }
        else if (resized) {
          startMacScrollbarFadeout();
        }
      }
    };

    myMouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        boolean inside = isOverThumb(e.getPoint());
        if (inside != myMouseIsOverThumb) {
          myMouseIsOverThumb = inside;
          startRegularThumbAnimator();
        }
      }
    };

    myMouseListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        // only restart animations when fading hasn't started yet
        if (myMacScrollbarFadeLevel == 0) {
          myMouseOverScrollbar = true;
          startMacScrollbarExpandAnimator();
          startMacScrollbarFadeout();
        }
      }
      
      @Override
      public void mouseExited(MouseEvent e) {
        if (myMouseIsOverThumb) {
          myMouseIsOverThumb = false;
          startRegularThumbAnimator();
        }

        if (myMouseOverScrollbar) {
          myMouseOverScrollbar = false;
          startMacScrollbarExpandAnimator();
          startMacScrollbarFadeout();
        }
      }
    };

    myHierarchyListener = new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if (e.getChanged() == scrollbar) {
          // scrollbar is added to the screen resources
          if ((HierarchyEvent.DISPLAYABILITY_CHANGED & e.getChangeFlags()) != 0) {
            updateGlobalListeners(false);
          }
        }

        if (e.getChanged() == scrollbar.getParent()) {
          // when scrollpane is shown first time, we 'blink' the scrollbars
          if ((HierarchyEvent.SHOWING_CHANGED & e.getChangeFlags()) != 0) {
            restart();
          }
        }
      }
    };
    myAWTMouseListener = new AWTEventListener() {
      public void eventDispatched(AWTEvent event) {
        if (event.getID() == MouseEvent.MOUSE_MOVED) {
          
            // user is moving inside the scrollpane of the scrollbar and fade-out hasn't started yet 
          Container scrollpane = SwingUtilities.getAncestorOfClass(JScrollPane.class, scrollbar);
          if (scrollpane != null) {
            Point loc = ((MouseEvent)event).getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(loc, scrollpane);
            if (scrollpane.contains(loc) && !myMacScrollbarHidden && myMacScrollbarFadeLevel == 0) {
              startMacScrollbarFadeout();
            }
          }
        }
      }
    };
    myNSScrollerListener = new NSScrollerHelper.ScrollbarStyleListener() {
      @Override
      public void styleChanged() {
        updateMacScrollbarStyle();
      }
    };
  }

  @Override
  protected ArrowButtonListener createArrowButtonListener() {
    return new ArrowButtonListener() {
      @Override
      public void mousePressed(MouseEvent event) {
      }

      @Override
      public void mouseReleased(MouseEvent event) {
      }
    };
  }

  protected boolean isMacOverlayScrollbar() {
    return myMacScrollerStyle == NSScrollerHelper.Style.Overlay && isMacOverlayScrollbarSupported();
  }
  
  public static boolean isMacOverlayScrollbarSupported() {
    return SystemInfo.isMac && !Registry.is("ide.mac.disableMacScrollbars");
  }

  private void updateMacScrollbarStyle() {
    NSScrollerHelper.Style style = NSScrollerHelper.getScrollerStyle();

    if (style != myMacScrollerStyle && scrollbar != null) {
      myMacScrollerStyle = style;

      updateStyleDefaults();
      restart();
      
      JScrollPane pane = JBScrollPane.findScrollPane(scrollbar);
      if (pane != null) pane.revalidate();
    }
  }

  public boolean alwaysShowTrack() {
    return !isMacOverlayScrollbar();
  }

  @Override
  public void layoutContainer(Container scrollbarContainer) {
    try {
      super.layoutContainer(scrollbarContainer);
    } catch (NullPointerException ignore) {
      //installUI is not performed yet or uninstallUI has set almost every field to null. Just ignore it //IDEA-89674
    }
  }

  /**
   * This is overridden only to increase the invalid area.
   * This ensures that whole track will be repainted in case of installed callback
   */
  @Override
  protected void setThumbBounds(int x, int y, int width, int height) {
    if (myRepaintCallback == null) {
      super.setThumbBounds(x, y, width, height);
    }
    else {
      // We want to repaint whole scrollbar even if thumb wasn't moved (on small scroll of a big panel)
      // Even if scrollbar wasn't changed itself, myRepaintCallback could need repaint

        /* Update thumbRect, and repaint the union of x,y,w,h and
         * the old thumbRect.
         */
      int minX = Math.min(x, trackRect.x);
      int minY = Math.min(y, trackRect.y);
      int maxX = Math.max(x + width, trackRect.x + trackRect.width);
      int maxY = Math.max(y + height, trackRect.y + trackRect.height);

      thumbRect.setBounds(x, y, width, height);
      scrollbar.repaint(minX, minY, maxX - minX, maxY - minY);

      // Once there is API to determine the mouse location this will need
      // to be changed.
      setThumbRollover(false);
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
    return Math.max(0, decrButton.getHeight());
  }
  public int getIncrementButtonHeight() {
    return Math.max(0, incrButton.getHeight());
  }

  private void startRegularThumbAnimator() {
    if (isMacOverlayScrollbar()) return;
    
    myThumbFadeAnimator.reset();
    if (scrollbar != null && scrollbar.getValueIsAdjusting() || myMouseIsOverThumb || Registry.is("ui.no.bangs.and.whistles")) {
      myThumbFadeAnimator.suspend();
      myThumbFadeColorShift = getAnimationColorShift();
    }
    else {
      myThumbFadeAnimator.resume();
    }
  }

  private void startMacScrollbarExpandAnimator() {
    if (!isMacOverlayScrollbar()) return;
    
    if (myMouseOverScrollbarExpandLevel == 0) {
      myMouseOverScrollbarExpandAnimator.reset();
      myMouseOverScrollbarExpandAnimator.suspend();
      if (myMouseOverScrollbar) {
        myMouseOverScrollbarExpandAnimator.resume();
      }
    }
  }

  private void startMacScrollbarFadeout() {
    startMacScrollbarFadeout(false);
  }

  private void startMacScrollbarFadeout(boolean now) {
    if (!isMacOverlayScrollbar()) return;

    myMacScrollbarFadeTimer.cancelAllRequests();

    if (now) {
      if (!myMacScrollbarHidden && !myMacScrollbarFadeAnimator.isRunning()) {
        myMacScrollbarFadeAnimator.resume();
      }
      return;
    }

    myMacScrollbarFadeAnimator.suspend();
    myMacScrollbarFadeAnimator.reset();
    myMacScrollbarHidden = false;
    myMacScrollbarFadeLevel = 0;

    JScrollBar sb = scrollbar; // concurrency in background editors initialization
    if (sb != null) {
      sb.repaint();

      if (!myMouseOverScrollbar && !sb.getValueIsAdjusting()) {
        myMacScrollbarFadeTimer.addRequest(new Runnable() {
          @Override
          public void run() {
            myMacScrollbarFadeAnimator.resume();
          }
        }, 700, null);
      }
    }
  }

  public static BasicScrollBarUI createNormal() {
    return new ButtonlessScrollBarUI();
  }

  public static BasicScrollBarUI createTransparent() {
    return new ButtonlessScrollBarUI() {
      @Override
      public boolean alwaysShowTrack() {
        return false;
      }
    };
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

    myMacScrollerStyle = NSScrollerHelper.getScrollerStyle();
    scrollbar.setFocusable(false);
    updateStyleDefaults();
  }

  private void updateStyleDefaults() {
    scrollbar.setOpaque(alwaysShowTrack());
  }

  @Override
  protected void installListeners() {
    initRegularThumbAnimator();
    initMacScrollbarAnimators();

    super.installListeners();
    scrollbar.addAdjustmentListener(myAdjustmentListener);
    scrollbar.addMouseListener(myMouseListener);
    scrollbar.addMouseMotionListener(myMouseMotionListener);
 
    scrollbar.addHierarchyListener(myHierarchyListener);
    updateGlobalListeners(false);

    restart();
  }

  private void restart() {
    startRegularThumbAnimator();
    startMacScrollbarFadeout();
  }

  private static final Method setValueFrom = ReflectionUtil.getDeclaredMethod(TrackListener.class, "setValueFrom", MouseEvent.class);
  static {
    LOG.assertTrue(setValueFrom != null, "Cannot get TrackListener.setValueFrom method");
  }

  @Override
  protected TrackListener createTrackListener() {
    return new TrackListener() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (scrollbar.isEnabled()
            && SwingUtilities.isLeftMouseButton(e)
            && !getThumbBounds().contains(e.getPoint())
            && NSScrollerHelper.getClickBehavior() == NSScrollerHelper.ClickBehavior.JumpToSpot
            && setValueFrom != null) {

          switch (scrollbar.getOrientation()) {
            case Adjustable.VERTICAL:
              offset = getThumbBounds().height / 2;
              break;
            case Adjustable.HORIZONTAL:
              offset = getThumbBounds().width / 2;
              break;
          }
          isDragging = true;
          try {
            setValueFrom.invoke(this, e);
          }
          catch (Exception ex) {
            LOG.error(ex);
          }

          return;
        }

        super.mousePressed(e);
      }
    };
  }
  
  private void updateGlobalListeners(boolean forceRemove) {
    boolean shouldAdd = scrollbar.isDisplayable();

    if (myGlobalListenersAdded && (!shouldAdd || forceRemove)) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(myAWTMouseListener);
      NSScrollerHelper.removeScrollbarStyleListener(myNSScrollerListener);
      myGlobalListenersAdded = false;
    }

    if (!myGlobalListenersAdded && shouldAdd && !forceRemove) {
      Toolkit.getDefaultToolkit().addAWTEventListener(myAWTMouseListener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
      NSScrollerHelper.addScrollbarStyleListener(myNSScrollerListener);
      myGlobalListenersAdded = true;
    }
  }
  
  private void initRegularThumbAnimator() {
    myThumbFadeAnimator = new Animator("Regular scrollbar thumb animator", FRAMES_COUNT, FRAMES_COUNT * 50, false) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        myThumbFadeColorShift = getAnimationColorShift();
        if (frame > DELAY_FRAMES) {
          myThumbFadeColorShift *= 1 - (double)(frame - DELAY_FRAMES) / (double)(totalFrames - DELAY_FRAMES);
        }

        if (scrollbar != null) {
          scrollbar.repaint(((ButtonlessScrollBarUI)scrollbar.getUI()).getThumbBounds());
        }
      }
    };
  }

  private void initMacScrollbarAnimators() {
    myMouseOverScrollbarExpandAnimator = new Animator("Mac scrollbar mouse over animator", 10, 200, false) {
      @Override
      protected void paintCycleEnd() {
        myMouseOverScrollbarExpandLevel = 1;
        if (scrollbar != null) scrollbar.repaint();
      }

      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        int delay = totalFrames / 2;
        int frameAfterDelay = frame - delay;

        if (frameAfterDelay > 0) {
          myMouseOverScrollbarExpandLevel = frameAfterDelay / (float)(totalFrames - delay);
          if (scrollbar != null) scrollbar.repaint();
        }
      }
    };

    myMacScrollbarFadeTimer = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myMacScrollbarFadeAnimator = new Animator("Mac scrollbar fade animator", 30, 300, false) {
      @Override
      protected void paintCycleEnd() {
        myMacScrollbarHidden = true;
        myMouseOverScrollbar = false;
        myMouseOverScrollbarExpandLevel = 0;

        if (scrollbar != null) scrollbar.repaint();
      }

      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        myMacScrollbarFadeLevel = frame / (float)totalFrames;
        if (scrollbar != null) scrollbar.repaint();
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

    scrollbar.removeAdjustmentListener(myAdjustmentListener);
    scrollbar.removeMouseListener(myMouseListener);
    scrollbar.removeMouseMotionListener(myMouseMotionListener);

    scrollbar.removeHierarchyListener(myHierarchyListener);
    updateGlobalListeners(true);

    Disposer.dispose(myThumbFadeAnimator);
    Disposer.dispose(myMouseOverScrollbarExpandAnimator);
    Disposer.dispose(myMacScrollbarFadeTimer);
    Disposer.dispose(myMacScrollbarFadeAnimator);
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    final int thickness = getThickness();
    return isVertical() ? new Dimension(thickness, thickness * 2) : new Dimension(thickness * 2, thickness);
  }

  protected int getThickness() {
    return isMacOverlayScrollbar() ? JBUI.scale(15) : JBUI.scale(13);
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
    if (isMacOverlayScrollbar() && !alwaysShowTrack() && !alwaysPaintThumb() && myMacScrollbarHidden) return false;  
    return super.contains(c, x, y);
  }

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    if (alwaysShowTrack() || myMouseOverScrollbarExpandLevel > 0) {
      doPaintTrack(g, c, trackBounds);
    }
  }

  protected void doPaintTrack(Graphics g, JComponent c, Rectangle bounds) {
    if (isMacOverlayScrollbar() && !alwaysShowTrack()) {
      bounds = getMacScrollBarBounds(bounds, false);
      boolean vertical = isVertical();

      final Paint paint;
      final Color start = adjustColor(UIUtil.getSlightlyDarkerColor(getTrackBackground()));
      final Color end = adjustColor(getTrackBackground().brighter());

      if (vertical) {
        paint = UIUtil.getGradientPaint(bounds.x + 1, bounds.y, start, bounds.width + 1, bounds.y, end);
      }
      else {
        paint = UIUtil.getGradientPaint(bounds.x, bounds.y + 1, start, bounds.x, bounds.height + 1, end);
      }

      Graphics2D g2d = (Graphics2D)g;
      g2d.setPaint(paint);
      g2d.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      g.setColor(adjustColor(start.darker()));
    }
    else {
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

    if (myRepaintCallback != null) {
      myRepaintCallback.call(g);
    }
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    doPaintThumb(g, thumbBounds);
  }

  private void doPaintThumb(Graphics g, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }

    if (isMacOverlayScrollbar()) {
      paintMacThumb(g, thumbBounds);
    }
    else {
      g.translate(thumbBounds.x, thumbBounds.y);
      paintMaxiThumb((Graphics2D)g, thumbBounds);
      g.translate(-thumbBounds.x, -thumbBounds.y);
    }
  }

  private void paintMacThumb(Graphics g, Rectangle thumbBounds) {
    if (isMacScrollbarHiddenAndXcodeLikeScrollbar()) return;

    thumbBounds = getMacScrollBarBounds(thumbBounds, true);
    Graphics2D g2d = (Graphics2D)g;
    RenderingHints oldHints = g2d.getRenderingHints();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    JBColor baseColor = new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return !isDark() ? Gray._0 : Gray._128;
      }
    });
    
    int arc = Math.min(thumbBounds.width, thumbBounds.height);

    if (alwaysPaintThumb()) {
      //noinspection UseJBColor
      g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), isDark() ? 100 : 40));
      g2d.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc);
      //g2d.drawRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc);
    }

    if (!myMacScrollbarHidden) {
      g2d.setColor(adjustColor(baseColor));
      g2d.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc);
    }
    g2d.setRenderingHints(oldHints);
  }
  
  protected boolean isDark() {
    return UIUtil.isUnderDarcula();
  }

  protected boolean alwaysPaintThumb() {
    return alwaysShowTrack();
  }

  protected Rectangle getMacScrollBarBounds(Rectangle baseBounds, boolean thumb) {
    boolean vertical = isVertical();

    int borderSize = 2;
    int baseSize = vertical ? baseBounds.width : baseBounds.height;

    int maxSize = baseSize - (thumb ? borderSize * 2 : 0);
    int minSize = Math.min(baseSize / 2, 7) + (thumb ? 0 : borderSize * 2);

    int currentSize = minSize + (int)(myMouseOverScrollbarExpandLevel * (maxSize - minSize));

    int currentBolderSize = thumb ? borderSize : 0;

    int x = baseBounds.x;
    int y = baseBounds.y;
    int width;
    int height;

    if (vertical) {
      x += baseBounds.width - currentSize - currentBolderSize;
      y += currentBolderSize;
      width = currentSize;
      height = baseBounds.height - currentBolderSize * 2;
    }
    else {
      x += currentBolderSize;
      y += baseBounds.height - currentSize - currentBolderSize;
      width = baseBounds.width - currentBolderSize * 2;
      height = currentSize;
    }

    width = Math.max(width, currentSize);
    height = Math.max(height, currentSize);

    return new Rectangle(x, y, width, height);
  }

  protected void paintMaxiThumb(Graphics2D g, Rectangle thumbBounds) {
    final boolean vertical = isVertical();
    int hGap = vertical ? 2 : 1;
    int vGap = vertical ? 1 : 2;

    int w = thumbBounds.width - hGap * 2;
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
    final int R = JBUI.scale(3);
    g.drawRoundRect(hGap, vGap, w, h, R, R);
    g.setStroke(stroke);
  }

  @Override
  public boolean getSupportsAbsolutePositioning() {
    return true;
  }

  protected Color adjustColor(Color c) {
    if (isMacOverlayScrollbar()) {
      int alpha = (int)((120 + myMouseOverScrollbarExpandLevel * 20) * (1 - myMacScrollbarFadeLevel));
      //noinspection UseJBColor
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
    else {
      if (myThumbFadeColorShift == 0) return c;
      final int sign = isDark() ? -1 : 1;
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

  protected boolean isMacScrollbarHiddenAndXcodeLikeScrollbar() {
    return myMacScrollbarHidden && isMacOverlayScrollbarSupported() && xcodeLikeScrollbar();
  }

  protected static boolean xcodeLikeScrollbar() {
    return Registry.is("editor.xcode.like.scrollbar");
  }

  public void registerRepaintCallback(ScrollbarRepaintCallback callback) {
    myRepaintCallback = callback;
  }

  private static class EmptyButton extends JButton {
    private EmptyButton() {
      setFocusable(false);
      setRequestFocusEnabled(false);
    }

    @Override
    public Dimension getMaximumSize() {
      return JBUI.emptySize();
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

  public interface ScrollbarRepaintCallback {
    void call(Graphics g);
  }
}
