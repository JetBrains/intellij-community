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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class ButtonlessScrollBarUI extends BasicScrollBarUI {
  private static final Logger LOG = Logger.getInstance(ButtonlessScrollBarUI.class);

  @Deprecated
  public static JBColor getTrackBackgroundDefault() {
    return new JBColor(LightColors.SLIGHTLY_GRAY, UIUtil.getListBackground());
  }

  @Deprecated
  public static JBColor getTrackBorderColorDefault() {
    return new JBColor(Gray._230, UIUtil.getListBackground());
  }

  private JBColor getTrackBackground() {
    return jbColor(LightColors.SLIGHTLY_GRAY, UIUtil.getListBackground());
  }
  
  private JBColor getTrackBorderColor() {
    return jbColor(Gray._230, UIUtil.getListBackground());
  }

  private JBColor jbColor(final Color regular, final Color dark) {
    return new JBColor(() -> isDark() ? dark : regular);
  }

  private int getAnimationColorShift() {
    return isDark() ? 20 : 40;
  }

  private final AdjustmentListener myAdjustmentListener;
  private final MouseMotionAdapter myMouseMotionListener;
  private final MouseAdapter myMouseListener;
  private final HierarchyListener myHierarchyListener;
  private final AWTEventListener myAWTMouseListener; // holds strong reference while a scroll bar in the hierarchy
  private final AWTEventListener myWeakListener;
  private final NSScrollerHelper.ScrollbarStyleListener myNSScrollerListener;
  private boolean myGlobalListenersAdded;

  private static final int DELAY_FRAMES = 4;
  private static final int FRAMES_COUNT = 10 + DELAY_FRAMES;

  private Animator myThumbFadeAnimator;
  private int myThumbFadeColorShift;
 
  private boolean myMouseIsOverThumb;
  private boolean myMouseOverScrollbar;
  private double myMouseOverScrollbarExpandLevel;

  private NSScrollerHelper.Style myMacScrollerStyle;
  private Animator myMouseOverScrollbarExpandAnimator;
  private Alarm myMacScrollbarFadeTimer;
  private Animator myMacScrollbarFadeAnimator;
  private double myMacScrollbarFadeLevel;
  private boolean myMacScrollbarHidden;

  private ScrollbarRepaintCallback myRepaintCallback;
  private boolean myDisposed;

  protected ButtonlessScrollBarUI() {
    myAdjustmentListener = new AdjustmentListener() {
      Point oldViewportPosition;
      Dimension oldViewportDimension;

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
          resized = vertical ? resizedV != 0 : resizedH != 0;
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
      @Override
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
    myWeakListener = new WeakLestener(myAWTMouseListener);
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
    }
    catch (NullPointerException ignore) {
      //installUI is not performed yet or uninstallUI has set almost every field to null. Just ignore it //IDEA-89674
    }
  }

  /**
   * This is overridden only to increase the invalid area.
   * This ensures that whole track will be repainted in case of installed callback
   */
  @Override
  protected void setThumbBounds(int x, int y, int width, int height) {
    // A logic to override Swing's "ScrollBar.alwaysShowThumb" property (set by GTK+ L&F).
    // When the property is set, the thumb fits entire width/height of a scroll bar (see BasicScrollBarUI.layoutVScrollbar()).
    // The fix detects such situations and resets thumb size, thus hiding the thumb when not needed.
    if (width > 0 && height > 0 && UIManager.getBoolean("ScrollBar.alwaysShowThumb") && !alwaysShowTrack()) {
      int w = trackRect.width, h = trackRect.height;
      if (w > h && w == width || w < h && h == height) {
        x = y = width = height = 0;
      }
    }

    if (myRepaintCallback != null) {
      // We want to repaint whole scrollbar even if thumb wasn't moved (on small scroll of a big panel)
      // Even if scrollbar wasn't changed itself, myRepaintCallback could need repaint
      scrollbar.repaint(trackRect);
    }

    super.setThumbBounds(x, y, width, height);
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
    if (myDisposed || isMacOverlayScrollbar()) return;
    
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
    if (myDisposed || !isMacOverlayScrollbar()) return;
    
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
    if (myDisposed || !isMacOverlayScrollbar()) return;

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

      Application application = ApplicationManager.getApplication();
      if (!myMouseOverScrollbar && !sb.getValueIsAdjusting() && (application == null || !application.isUnitTestMode())) {
        myMacScrollbarFadeTimer.addRequest(() -> myMacScrollbarFadeAnimator.resume(), 700, null);
      }
    }
  }

  @Deprecated
  public static BasicScrollBarUI createNormal() {
    return new ButtonlessScrollBarUI();
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
    myDisposed = false;

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
      Toolkit.getDefaultToolkit().removeAWTEventListener(myWeakListener);
      NSScrollerHelper.removeScrollbarStyleListener(myNSScrollerListener);
      myGlobalListenersAdded = false;
    }

    if (!myGlobalListenersAdded && shouldAdd && !forceRemove) {
      Toolkit.getDefaultToolkit().addAWTEventListener(myWeakListener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
      NSScrollerHelper.addScrollbarStyleListener(myNSScrollerListener);
      myGlobalListenersAdded = true;
    }
  }
  
  private void initRegularThumbAnimator() {
    if (!myDisposed) {
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
  }

  private void initMacScrollbarAnimators() {
    if (!myDisposed) {
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

    if (myThumbFadeAnimator != null) {
      Disposer.dispose(myThumbFadeAnimator);
      myThumbFadeAnimator = null;
    }
    if (myMouseOverScrollbarExpandAnimator != null) {
      Disposer.dispose(myMouseOverScrollbarExpandAnimator);
    }
    if (myMacScrollbarFadeTimer != null) {
      Disposer.dispose(myMacScrollbarFadeTimer);
    }
    if (myMacScrollbarFadeAnimator != null) {
      Disposer.dispose(myMacScrollbarFadeAnimator);
    }
    myDisposed = true;
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    final int thickness = getThickness();
    return isVertical() ? new Dimension(thickness, thickness * 2) : new Dimension(thickness * 2, thickness);
  }

  protected int getThickness() {
    return JBUI.scale(isMacOverlayScrollbar() ? 15 : 13);
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
    RegionPainter<Object> painter = UIUtil.getClientProperty(c, JBScrollBar.TRACK);
    if (painter != null) {
      painter.paint((Graphics2D)g, trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, null);
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
      int x = scrollbar.getComponentOrientation().isLeftToRight() ? bounds.x : bounds.x + bounds.width - 1;
      g.drawLine(x, bounds.y, x, bounds.y + bounds.height);
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
      Rectangle bounds = new Rectangle(thumbBounds);
      if (isThumbTranslucent()) {
        Alignment alignment = Alignment.get(scrollbar);
        if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
          int offset = getThumbOffset(bounds.width);
          if (offset > 0) {
            bounds.width -= offset;
            if (alignment == Alignment.RIGHT) bounds.x += offset;
          }
        }
        else {
          int offset = getThumbOffset(bounds.height);
          if (offset > 0) {
            bounds.height -= offset;
            if (alignment == Alignment.BOTTOM) bounds.y += offset;
          }
        }
      }
      else if (SystemInfo.isMac) {
        boolean vertical = scrollbar == null || Adjustable.VERTICAL == scrollbar.getOrientation();
        bounds.x += vertical ? 1 : 0;
        bounds.y += vertical ? 0 : 1;
        bounds.width -= vertical ? 1 : 0;
        bounds.height -= vertical ? 0 : 1;
      }
      else {
        bounds.x += 1;
        bounds.y += 1;
        bounds.width -= 2;
        bounds.height -= 2;
      }
      if (SystemInfo.isMac) {
        int max = JBUI.scale(12);
        if (max < bounds.width && bounds.width < bounds.height) {
          bounds.x += (bounds.width - max) / 2;
          bounds.width = max;
        }
        else if (max < bounds.height && bounds.height < bounds.width) {
          bounds.y += (bounds.height - max) / 2;
          bounds.height = max;
        }
        float value = (float)myThumbFadeColorShift / getAnimationColorShift();
        RegionPainter<Float> painter = isDark() ? JBScrollPane.MAC_THUMB_DARK_PAINTER : JBScrollPane.MAC_THUMB_PAINTER;
        painter.paint((Graphics2D)g, bounds.x, bounds.y, bounds.width, bounds.height, value);
      }
      else {
        float value = (float)myThumbFadeColorShift / getAnimationColorShift();
        RegionPainter<Float> painter = isDark() ? JBScrollPane.THUMB_DARK_PAINTER : JBScrollPane.THUMB_PAINTER;
        painter.paint((Graphics2D)g, bounds.x, bounds.y, bounds.width, bounds.height, value);
      }
    }
  }

  @Deprecated
  protected boolean isThumbTranslucent() {
    return scrollbar == null || !scrollbar.isOpaque();
  }

  @Deprecated
  protected int getThumbOffset(int value) {
    // com.intellij.ui.components.AbstractScrollBarUI.scale
    float scale = JBUI.scale(10);
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (UIUtil.getComponentStyle(scrollbar)) {
      case LARGE:
        scale *= 1.15f;
        break;
      case SMALL:
        scale *= 0.857f;
        break;
      case MINI:
        scale *= 0.714f;
        break;
    }
    return value - (int)scale;
  }

  private void paintMacThumb(Graphics g, Rectangle thumbBounds) {
    if (isMacScrollbarHiddenAndXcodeLikeScrollbar()) return;

    thumbBounds = getMacScrollBarBounds(thumbBounds, true);
    Graphics2D g2d = (Graphics2D)g;

    float value = (float)(1 - myMacScrollbarFadeLevel);
    if (!myMacScrollbarHidden || alwaysPaintThumb()) {
      RegionPainter<Float> painter = isDark() ? JBScrollPane.MAC_THUMB_DARK_PAINTER : JBScrollPane.MAC_THUMB_PAINTER;
      painter.paint(g2d, thumbBounds.x - 2, thumbBounds.y - 2, thumbBounds.width + 4, thumbBounds.height + 4, value);
    }
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
    int minSize = Math.min(baseSize / 2, JBUI.scale(7)) + (thumb ? 0 : borderSize * 2);

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

  protected boolean isVertical() {
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

  public static class Transparent extends ButtonlessScrollBarUI {
    @Override
    public boolean alwaysShowTrack() {
      return false;
    }
  }

  private static final class WeakLestener implements AWTEventListener {
    private final WeakReference<AWTEventListener> myReference;

    private WeakLestener(AWTEventListener listener) {
      myReference = new WeakReference<>(listener);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      AWTEventListener listener = myReference.get();
      if (listener != null) {
        listener.eventDispatched(event);
      }
      else {
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
      }
    }
  }
}
