/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.Field;

import static com.intellij.util.ui.JBUI.emptyInsets;

public class JBScrollPane extends JScrollPane {
  /**
   * This key is used to specify which colors should use the scroll bars on the pane.
   * If a client property is set to {@code true} the bar's brightness
   * will be modified according to the view's background.
   *
   * @see UIUtil#putClientProperty
   * @see UIUtil#isUnderDarcula
   */
  public static final Key<Boolean> BRIGHTNESS_FROM_VIEW = Key.create("JB_SCROLL_PANE_BRIGHTNESS_FROM_VIEW");

  @Deprecated
  public static final RegionPainter<Float> THUMB_PAINTER = ScrollPainter.EditorThumb.DEFAULT;

  @Deprecated
  public static final RegionPainter<Float> THUMB_DARK_PAINTER = ScrollPainter.EditorThumb.DARCULA;

  @Deprecated
  public static final RegionPainter<Float> MAC_THUMB_PAINTER = ScrollPainter.EditorThumb.Mac.DEFAULT;

  @Deprecated
  public static final RegionPainter<Float> MAC_THUMB_DARK_PAINTER = ScrollPainter.EditorThumb.Mac.DARCULA;

  private static final Logger LOG = Logger.getInstance(JBScrollPane.class);

  private ScrollSource myScrollSource = ScrollSource.UNKNOWN;
  private double myWheelRotation;

  private int myViewportBorderWidth = -1;
  private volatile boolean myBackgroundRequested; // avoid cyclic references

  public JBScrollPane(int viewportWidth) {
    init(false);
    myViewportBorderWidth = viewportWidth;
    updateViewportBorder();
  }

  public JBScrollPane() {
    init();
  }

  public JBScrollPane(Component view) {
    super(view);
    init();
  }

  public JBScrollPane(int vsbPolicy, int hsbPolicy) {
    super(vsbPolicy, hsbPolicy);
    init();
  }

  public JBScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
    super(view, vsbPolicy, hsbPolicy);
    init();
  }

  @Override
  public Color getBackground() {
    Color color = super.getBackground();
    if (!myBackgroundRequested && EventQueue.isDispatchThread() && ScrollSettings.isBackgroundFromView()) {
      if (!isBackgroundSet() || color instanceof UIResource) {
        Component child = getViewport();
        if (child != null) {
          try {
            myBackgroundRequested = true;
            return child.getBackground();
          }
          finally {
            myBackgroundRequested = false;
          }
        }
      }
    }
    return color;
  }

  static Color getViewBackground(JScrollPane pane) {
    if (pane == null) return null;
    JViewport viewport = pane.getViewport();
    if (viewport == null) return null;
    Component view = viewport.getView();
    if (view == null) return null;
    return view.getBackground();
  }

  public static JScrollPane findScrollPane(Component c) {
    if (c == null) return null;

    if (!(c instanceof JViewport)) {
      Container vp = c.getParent();
      if (vp instanceof JViewport) c = vp;
    }

    c = c.getParent();
    if (!(c instanceof JScrollPane)) return null;

    return (JScrollPane)c;
  }

  private void init() {
    init(true);
  }

  private void init(boolean setupCorners) {
    setLayout(new Layout());

    if (setupCorners) {
      setupCorners();
    }
  }

  protected void setupCorners() {
    setBorder(IdeBorderFactory.createBorder());
    setCorner(UPPER_RIGHT_CORNER, new Corner(UPPER_RIGHT_CORNER));
    setCorner(UPPER_LEFT_CORNER, new Corner(UPPER_LEFT_CORNER));
    setCorner(LOWER_RIGHT_CORNER, new Corner(LOWER_RIGHT_CORNER));
    setCorner(LOWER_LEFT_CORNER, new Corner(LOWER_LEFT_CORNER));
  }

  @Override
  public void setUI(ScrollPaneUI ui) {
    super.setUI(ui);
    updateViewportBorder();
    if (ui instanceof BasicScrollPaneUI) {
      try {
        Field field = BasicScrollPaneUI.class.getDeclaredField("mouseScrollListener");
        field.setAccessible(true);
        Object value = field.get(ui);
        if (value instanceof MouseWheelListener) {
          MouseWheelListener oldListener = (MouseWheelListener)value;
          MouseWheelListener newListener = event -> {
            if (isScrollEvent(event)) {
              Object source = event.getSource();
              if (source instanceof JScrollPane) {
                JScrollPane pane = (JScrollPane)source;
                if (pane.isWheelScrollingEnabled()) {
                  JScrollBar bar = event.isShiftDown() ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
                  if (bar != null && bar.isVisible()) {
                    if (!(bar instanceof JBScrollBar && ((JBScrollBar)bar).handleMouseWheelEvent(event))) {
                      oldListener.mouseWheelMoved(event);
                    }
                  }
                }
              }
            }
          };
          field.set(ui, newListener);
          // replace listener if field updated successfully
          removeMouseWheelListener(oldListener);
          addMouseWheelListener(newListener);
        }
      }
      catch (Exception exception) {
        LOG.warn(exception);
      }
    }
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    return isOptimizedDrawingEnabledFor(getVerticalScrollBar()) &&
           isOptimizedDrawingEnabledFor(getHorizontalScrollBar());
  }

  /**
   * Returns {@code false} for visible translucent scroll bars, or {@code true} otherwise.
   * It is needed to repaint translucent scroll bars on viewport repainting.
   */
  private static boolean isOptimizedDrawingEnabledFor(JScrollBar bar) {
    return bar == null || !bar.isVisible() || (bar.isOpaque() && bar.isOptimizedDrawingEnabled());
  }

  private void updateViewportBorder() {
    if (getViewportBorder() instanceof ViewportBorder) {
      setViewportBorder(new ViewportBorder(myViewportBorderWidth >= 0 ? myViewportBorderWidth : 1));
    }
  }

  public static ViewportBorder createIndentBorder() {
    return new ViewportBorder(2);
  }

  @Override
  public JScrollBar createVerticalScrollBar() {
    return new JBScrollBar(Adjustable.VERTICAL);
  }

  @NotNull
  @Override
  public JScrollBar createHorizontalScrollBar() {
    return new JBScrollBar(Adjustable.HORIZONTAL);
  }

  @Override
  protected JViewport createViewport() {
    return new JBViewport();
  }

  @Deprecated
  protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
    ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
    return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
  }

  public static boolean canBePreprocessed(MouseEvent e, JScrollBar bar) {
    if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_PRESSED) {
      ScrollBarUI ui = bar.getUI();
      if (ui instanceof BasicScrollBarUI) {
        BasicScrollBarUI bui = (BasicScrollBarUI)ui;
        try {
          Rectangle rect = (Rectangle)ReflectionUtil.getDeclaredMethod(BasicScrollBarUI.class, "getThumbBounds", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(bui);
          Point point = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), bar);
          return !rect.contains(point);
        }
        catch (Exception e1) {
          return true;
        }
      }
      else if (ui instanceof DefaultScrollBarUI) {
        DefaultScrollBarUI dui = (DefaultScrollBarUI)ui;
        Point point = e.getLocationOnScreen();
        SwingUtilities.convertPointFromScreen(point, bar);
        return !dui.isThumbContains(point.x, point.y);
      }
    }
    return true;
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    boolean hasAbsoluteDelta = ScrollSettings.isPixelPerfectEnabled();
    myScrollSource = hasAbsoluteDelta ? ScrollSource.TOUCHPAD : ScrollSource.MOUSE_WHEEL;
    myWheelRotation = e.getPreciseWheelRotation();
    super.processMouseWheelEvent(e);
    myScrollSource = ScrollSource.UNKNOWN;
  }

  int getInitialDelay(boolean valueIsAdjusting) {
    ScrollSource source = valueIsAdjusting ? ScrollSource.SCROLLBAR : myScrollSource;
    return source.getInterpolationDelay(myWheelRotation);
  }

  private static class Corner extends JPanel {
    private final String myPos;

    public Corner(String pos) {
      myPos = pos;
      ScrollColorProducer.setBackground(this);
      ScrollColorProducer.setForeground(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  private static class ViewportBorder extends LineBorder {
    public ViewportBorder(int thickness) {
      super(null, thickness);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      updateColor(c);
      super.paintBorder(c, g, x, y, width, height);
    }

    private void updateColor(Component c) {
      if (!(c instanceof JScrollPane)) return;
      lineColor = getViewBackground((JScrollPane)c);
    }
  }

  /**
   * These client properties modify a scroll pane layout.
   * Use the class object as a property key.
   *
   * @see #putClientProperty(Object, Object)
   */
  public enum Flip {
    NONE, VERTICAL, HORIZONTAL, BOTH
  }

  /**
   * These client properties show a component position on a scroll pane.
   * It is set by internal layout manager of the scroll pane.
   */
  public enum Alignment {
    TOP, LEFT, RIGHT, BOTTOM;

    public static Alignment get(JComponent component) {
      if (component != null) {
        Object property = component.getClientProperty(Alignment.class);
        if (property instanceof Alignment) return (Alignment)property;

        Container parent = component.getParent();
        if (parent instanceof JScrollPane) {
          JScrollPane pane = (JScrollPane)parent;
          if (component == pane.getColumnHeader()) {
            return TOP;
          }
          if (component == pane.getHorizontalScrollBar()) {
            return BOTTOM;
          }
          boolean ltr = pane.getComponentOrientation().isLeftToRight();
          if (component == pane.getVerticalScrollBar()) {
            return ltr ? RIGHT : LEFT;
          }
          if (component == pane.getRowHeader()) {
            return ltr ? LEFT : RIGHT;
          }
        }
        // assume alignment for a scroll bar,
        // which is not contained in a scroll pane
        if (component instanceof JScrollBar) {
          JScrollBar bar = (JScrollBar)component;
          switch (bar.getOrientation()) {
            case Adjustable.HORIZONTAL:
              return BOTTOM;
            case Adjustable.VERTICAL:
              return bar.getComponentOrientation().isLeftToRight()
                     ? RIGHT
                     : LEFT;
          }
        }
      }
      return null;
    }
  }

  /**
   * ScrollPaneLayout implementation that supports
   * ScrollBar flipping and non-opaque ScrollBars.
   */
  private static class Layout extends ScrollPaneLayout {
    private static final Insets EMPTY_INSETS = emptyInsets();

    @Override
    public void layoutContainer(Container parent) {
      JScrollPane pane = (JScrollPane)parent;
      // Calculate inner bounds of the scroll pane
      Rectangle bounds = new Rectangle(pane.getWidth(), pane.getHeight());
      JBInsets.removeFrom(bounds, pane.getInsets());
      // Determine positions of scroll bars on the scroll pane
      Object property = pane.getClientProperty(Flip.class);
      Flip flip = property instanceof Flip ? (Flip)property : Flip.NONE;
      boolean hsbOnTop = flip == Flip.BOTH || flip == Flip.VERTICAL;
      boolean vsbOnLeft = pane.getComponentOrientation().isLeftToRight()
                          ? flip == Flip.BOTH || flip == Flip.HORIZONTAL
                          : flip == Flip.NONE || flip == Flip.VERTICAL;
      // If there's a visible row header remove the space it needs.
      // The row header is treated as if it were fixed width, arbitrary height.
      Rectangle rowHeadBounds = new Rectangle(bounds.x, 0, 0, 0);
      if (rowHead != null && rowHead.isVisible()) {
        rowHeadBounds.width = min(bounds.width, rowHead.getPreferredSize().width);
        bounds.width -= rowHeadBounds.width;
        if (vsbOnLeft) {
          rowHeadBounds.x += bounds.width;
        }
        else {
          bounds.x += rowHeadBounds.width;
        }
      }
      // If there's a visible column header remove the space it needs.
      // The column header is treated as if it were fixed height, arbitrary width.
      Rectangle colHeadBounds = new Rectangle(0, bounds.y, 0, 0);
      if (colHead != null && colHead.isVisible()) {
        colHeadBounds.height = min(bounds.height, colHead.getPreferredSize().height);
        bounds.height -= colHeadBounds.height;
        if (hsbOnTop) {
          colHeadBounds.y += bounds.height;
        }
        else {
          bounds.y += colHeadBounds.height;
        }
      }
      // If there's a JScrollPane.viewportBorder, remove the space it occupies
      Border border = pane.getViewportBorder();
      Insets insets = border == null ? null : border.getBorderInsets(parent);
      JBInsets.removeFrom(bounds, insets);
      if (insets == null) insets = EMPTY_INSETS;
      // At this point:
      // colHeadBounds is correct except for its width and x
      // rowHeadBounds is correct except for its height and y
      // bounds - the space available for the viewport and scroll bars
      // Once we're through computing the dimensions of these three parts
      // we can go back and set the bounds for the corners and the dimensions of
      // colHeadBounds.x, colHeadBounds.width, rowHeadBounds.y, rowHeadBounds.height.
      boolean isEmpty = bounds.width < 0 || bounds.height < 0;
      Component view = viewport == null ? null : viewport.getView();
      Dimension viewPreferredSize = view == null ? new Dimension() : view.getPreferredSize();
      if (view instanceof JComponent && !view.isPreferredSizeSet()) {
        JBInsets.removeFrom(viewPreferredSize, JBViewport.getViewInsets((JComponent)view));
      }
      Dimension viewportExtentSize = viewport == null ? new Dimension() : viewport.toViewCoordinates(bounds.getSize());
      // If the view is tracking the viewports width we don't bother with a horizontal scrollbar.
      // If the view is tracking the viewports height we don't bother with a vertical scrollbar.
      Scrollable scrollable = null;
      boolean viewTracksViewportWidth = false;
      boolean viewTracksViewportHeight = false;
      // Don't bother checking the Scrollable methods if there is no room for the viewport,
      // we aren't going to show any scroll bars in this case anyway.
      if (!isEmpty && view instanceof Scrollable) {
        scrollable = (Scrollable)view;
        viewTracksViewportWidth = scrollable.getScrollableTracksViewportWidth();
        viewTracksViewportHeight = scrollable.getScrollableTracksViewportHeight();
      }
      // workaround for installed JBViewport.ViewBorder:
      // do not hide scroll bars if view is not aligned
      Point viewLocation = new Point();
      if (view != null) viewLocation = view.getLocation(viewLocation);
      // If there's a vertical scroll bar and we need one, allocate space for it.
      // A vertical scroll bar is considered to be fixed width, arbitrary height.
      boolean vsbOpaque = false;
      boolean vsbNeeded = false;
      int vsbPolicy = pane.getVerticalScrollBarPolicy();
      if (!isEmpty && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
        vsbNeeded = vsbPolicy == VERTICAL_SCROLLBAR_ALWAYS
                    || !viewTracksViewportHeight && (viewPreferredSize.height > viewportExtentSize.height || viewLocation.y != 0);
      }
      Rectangle vsbBounds = new Rectangle(0, bounds.y - insets.top, 0, 0);
      if (vsb != null) {
        if (isAlwaysOpaque(view)) vsb.setOpaque(true);
        vsbOpaque = vsb.isOpaque();
        if (vsbNeeded) {
          adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
          if (vsbOpaque && viewport != null) {
            viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
          }
        }
      }
      // If there's a horizontal scroll bar and we need one, allocate space for it.
      // A horizontal scroll bar is considered to be fixed height, arbitrary width.
      boolean hsbOpaque = false;
      boolean hsbNeeded = false;
      int hsbPolicy = pane.getHorizontalScrollBarPolicy();
      if (!isEmpty && hsbPolicy != HORIZONTAL_SCROLLBAR_NEVER) {
        hsbNeeded = hsbPolicy == HORIZONTAL_SCROLLBAR_ALWAYS
                    || !viewTracksViewportWidth && (viewPreferredSize.width > viewportExtentSize.width || viewLocation.x != 0);
      }
      Rectangle hsbBounds = new Rectangle(bounds.x - insets.left, 0, 0, 0);
      if (hsb != null) {
        if (isAlwaysOpaque(view)) hsb.setOpaque(true);
        hsbOpaque = hsb.isOpaque();
        if (hsbNeeded) {
          adjustForHSB(bounds, insets, hsbBounds, hsbOpaque, hsbOnTop);
          if (hsbOpaque && viewport != null) {
            // If we added the horizontal scrollbar and reduced the vertical space
            // we may have to add the vertical scrollbar, if that hasn't been done so already.
            if (vsb != null && !vsbNeeded && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
              viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
              vsbNeeded = viewPreferredSize.height > viewportExtentSize.height || viewLocation.y != 0;
              if (vsbNeeded) adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
            }
          }
        }
      }
      // Set the size of the viewport first, and then recheck the Scrollable methods.
      // Some components base their return values for the Scrollable methods on the size of the viewport,
      // so that if we don't ask after resetting the bounds we may have gotten the wrong answer.
      if (viewport != null) {
        viewport.setBounds(bounds);
        if (scrollable != null && hsbOpaque && vsbOpaque) {
          viewTracksViewportWidth = scrollable.getScrollableTracksViewportWidth();
          viewTracksViewportHeight = scrollable.getScrollableTracksViewportHeight();
          viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());

          boolean vsbNeededOld = vsbNeeded;
          if (vsb != null && vsbPolicy == VERTICAL_SCROLLBAR_AS_NEEDED) {
            boolean vsbNeededNew = !viewTracksViewportHeight && viewPreferredSize.height > viewportExtentSize.height || viewLocation.y != 0;
            if (vsbNeeded != vsbNeededNew) {
              vsbNeeded = vsbNeededNew;
              if (vsbNeeded) {
                adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
              }
              else if (vsbOpaque) {
                bounds.width += vsbBounds.width;
              }
              if (vsbOpaque) viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
            }
          }
          boolean hsbNeededOld = hsbNeeded;
          if (hsb != null && hsbPolicy == HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            boolean hsbNeededNew = !viewTracksViewportWidth && viewPreferredSize.width > viewportExtentSize.width || viewLocation.x != 0;
            if (hsbNeeded != hsbNeededNew) {
              hsbNeeded = hsbNeededNew;
              if (hsbNeeded) {
                adjustForHSB(bounds, insets, hsbBounds, hsbOpaque, hsbOnTop);
              }
              else if (hsbOpaque) {
                bounds.height += hsbBounds.height;
              }
              if (hsbOpaque && vsb != null && !vsbNeeded && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
                viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
                vsbNeeded = viewPreferredSize.height > viewportExtentSize.height || viewLocation.y != 0;
                if (vsbNeeded) adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
              }
            }
          }
          if (hsbNeededOld != hsbNeeded || vsbNeededOld != vsbNeeded) {
            viewport.setBounds(bounds);
            // You could argue that we should recheck the Scrollable methods again until they stop changing,
            // but they might never stop changing, so we stop here and don't do any additional checks.
          }
        }
      }
      // Set the bounds of the row header.
      rowHeadBounds.y = bounds.y - insets.top;
      rowHeadBounds.height = bounds.height + insets.top + insets.bottom;
      boolean fillLowerCorner = false;
      if (rowHead != null) {
        if (hsbOpaque) {
          Component corner = hsbOnTop ? (vsbOnLeft ? upperRight : upperLeft) : (vsbOnLeft ? lowerRight : lowerLeft);
          fillLowerCorner = corner == null && UIManager.getBoolean("ScrollPane.fillLowerCorner");
          if (!fillLowerCorner && ScrollSettings.isHeaderOverCorner(viewport)) {
            if (hsbOnTop) rowHeadBounds.y -= hsbBounds.height;
            rowHeadBounds.height += hsbBounds.height;
          }
        }
        rowHead.setBounds(rowHeadBounds);
        rowHead.putClientProperty(Alignment.class, vsbOnLeft ? Alignment.RIGHT : Alignment.LEFT);
      }
      // Set the bounds of the column header.
      colHeadBounds.x = bounds.x - insets.left;
      colHeadBounds.width = bounds.width + insets.left + insets.right;
      boolean fillUpperCorner = false;
      if (colHead != null) {
        if (vsbOpaque) {
          Component corner = vsbOnLeft ? (hsbOnTop ? lowerLeft : upperLeft) : (hsbOnTop ? lowerRight : upperRight);
          fillUpperCorner = corner == null && UIManager.getBoolean("ScrollPane.fillUpperCorner");
          if (!fillUpperCorner && ScrollSettings.isHeaderOverCorner(viewport)) {
            if (vsbOnLeft) colHeadBounds.x -= vsbBounds.width;
            colHeadBounds.width += vsbBounds.width;
          }
        }
        colHead.setBounds(colHeadBounds);
        colHead.putClientProperty(Alignment.class, hsbOnTop ? Alignment.BOTTOM : Alignment.TOP);
      }
      // Calculate overlaps for translucent scroll bars
      int overlapWidth = 0;
      int overlapHeight = 0;
      if (vsbNeeded && !vsbOpaque && hsbNeeded && !hsbOpaque) {
        overlapWidth = vsbBounds.width; // shrink horizontally
        //overlapHeight = hsbBounds.height; // shrink vertically
      }
      // Set the bounds of the vertical scroll bar.
      vsbBounds.y = bounds.y - insets.top;
      vsbBounds.height = bounds.height + insets.top + insets.bottom;
      if (vsb != null) {
        vsb.setVisible(vsbNeeded);
        if (vsbNeeded) {
          if (fillUpperCorner) {
            // This is used primarily for GTK L&F, which needs to extend
            // the vertical scrollbar to fill the upper corner near the column header.
            // Note that we skip this step (and use the default behavior)
            // if the user has set a custom corner component.
            if (!hsbOnTop) vsbBounds.y -= colHeadBounds.height;
            vsbBounds.height += colHeadBounds.height;
          }
          int overlapY = !hsbOnTop ? 0 : overlapHeight;
          vsb.setBounds(vsbBounds.x, vsbBounds.y + overlapY, vsbBounds.width, vsbBounds.height - overlapHeight);
          vsb.putClientProperty(Alignment.class, vsbOnLeft ? Alignment.LEFT : Alignment.RIGHT);
        }
        // Modify the bounds of the translucent scroll bar.
        if (!vsbOpaque) {
          if (!vsbOnLeft) vsbBounds.x += vsbBounds.width;
          vsbBounds.width = 0;
        }
      }
      // Set the bounds of the horizontal scroll bar.
      hsbBounds.x = bounds.x - insets.left;
      hsbBounds.width = bounds.width + insets.left + insets.right;
      if (hsb != null) {
        hsb.setVisible(hsbNeeded);
        if (hsbNeeded) {
          if (fillLowerCorner) {
            // This is used primarily for GTK L&F, which needs to extend
            // the horizontal scrollbar to fill the lower corner near the row header.
            // Note that we skip this step (and use the default behavior)
            // if the user has set a custom corner component.
            if (!vsbOnLeft) hsbBounds.x -= rowHeadBounds.width;
            hsbBounds.width += rowHeadBounds.width;
          }
          int overlapX = !vsbOnLeft ? 0 : overlapWidth;
          hsb.setBounds(hsbBounds.x + overlapX, hsbBounds.y, hsbBounds.width - overlapWidth, hsbBounds.height);
          hsb.putClientProperty(Alignment.class, hsbOnTop ? Alignment.TOP : Alignment.BOTTOM);
        }
        // Modify the bounds of the translucent scroll bar.
        if (!hsbOpaque) {
          if (!hsbOnTop) hsbBounds.y += hsbBounds.height;
          hsbBounds.height = 0;
        }
      }
      // Set the bounds of the corners.
      Rectangle left = vsbOnLeft ? vsbBounds : rowHeadBounds;
      Rectangle right = vsbOnLeft ? rowHeadBounds : vsbBounds;
      Rectangle upper = hsbOnTop ? hsbBounds : colHeadBounds;
      Rectangle lower = hsbOnTop ? colHeadBounds : hsbBounds;
      if (lowerLeft != null) {
        Rectangle lowerLeftBounds = new Rectangle(left.x, left.y + left.height, 0, 0);
        if (left.width > 0 && lower.height > 0) updateCornerBounds(lowerLeftBounds, lower.x, lower.y + lower.height);
        lowerLeft.setBounds(lowerLeftBounds);
      }
      if (lowerRight != null) {
        Rectangle lowerRightBounds = new Rectangle(lower.x + lower.width, right.y + right.height, 0, 0);
        if (right.width > 0 && lower.height > 0) updateCornerBounds(lowerRightBounds, right.x + right.width, lower.y + lower.height);
        lowerRight.setBounds(lowerRightBounds);
      }
      if (upperLeft != null) {
        Rectangle upperLeftBounds = new Rectangle(left.x, upper.y, 0, 0);
        if (left.width > 0 && upper.height > 0) updateCornerBounds(upperLeftBounds, upper.x, left.y);
        upperLeft.setBounds(upperLeftBounds);
      }
      if (upperRight != null) {
        Rectangle upperRightBounds = new Rectangle(upper.x + upper.width, upper.y, 0, 0);
        if (right.width > 0 && upper.height > 0) updateCornerBounds(upperRightBounds, right.x + right.width, right.y);
        upperRight.setBounds(upperRightBounds);
      }
      if (!vsbOpaque && vsbNeeded || !hsbOpaque && hsbNeeded) {
        fixComponentZOrder(vsb, 0);
        fixComponentZOrder(viewport, -1);
      }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      /* Sync the (now obsolete) policy fields with the
         * JScrollPane.
         */
      JScrollPane scrollPane = (JScrollPane)parent;
      vsbPolicy = scrollPane.getVerticalScrollBarPolicy();
      hsbPolicy = scrollPane.getHorizontalScrollBarPolicy();

      Insets insets = parent.getInsets();
      int prefWidth = insets.left + insets.right;
      int prefHeight = insets.top + insets.bottom;

        /* Note that viewport.getViewSize() is equivalent to
         * viewport.getView().getPreferredSize() modulo a null
         * view or a view whose size was explicitly set.
         */

      Dimension extentSize = null;
      Dimension viewSize = null;
      Component view = null;

      if (viewport != null) {
        extentSize = viewport.getPreferredSize();
        view = viewport.getView();
        if (view != null) {
          viewSize = view.getPreferredSize();
        } else {
          viewSize = new Dimension(0, 0);
        }
      }

        /* If there's a viewport add its preferredSize.
         */

      if (extentSize != null) {
        prefWidth += extentSize.width;
        prefHeight += extentSize.height;
      }

        /* If there's a JScrollPane.viewportBorder, add its insets.
         */

      Border viewportBorder = scrollPane.getViewportBorder();
      if (viewportBorder != null) {
        Insets vpbInsets = viewportBorder.getBorderInsets(parent);
        prefWidth += vpbInsets.left + vpbInsets.right;
        prefHeight += vpbInsets.top + vpbInsets.bottom;
      }

        /* If a header exists and it's visible, factor its
         * preferred size in.
         */

      if ((rowHead != null) && rowHead.isVisible()) {
        prefWidth += rowHead.getPreferredSize().width;
      }

      if ((colHead != null) && colHead.isVisible()) {
        prefHeight += colHead.getPreferredSize().height;
      }

        /* If a scrollbar is going to appear, factor its preferred size in.
         * If the scrollbars policy is AS_NEEDED, this can be a little
         * tricky:
         *
         * - If the view is a Scrollable then scrollableTracksViewportWidth
         * and scrollableTracksViewportHeight can be used to effectively
         * disable scrolling (if they're true) in their respective dimensions.
         *
         * - Assuming that a scrollbar hasn't been disabled by the
         * previous constraint, we need to decide if the scrollbar is going
         * to appear to correctly compute the JScrollPanes preferred size.
         * To do this we compare the preferredSize of the viewport (the
         * extentSize) to the preferredSize of the view.  Although we're
         * not responsible for laying out the view we'll assume that the
         * JViewport will always give it its preferredSize.
         */

      if ((vsb != null) && (vsbPolicy != VERTICAL_SCROLLBAR_NEVER)) {
        boolean considerVsbWidth = vsb.isOpaque() || isAlwaysOpaque(view);
        if (vsbPolicy == VERTICAL_SCROLLBAR_ALWAYS) {
          if (considerVsbWidth) prefWidth += vsb.getPreferredSize().width;
        }
        else if ((viewSize != null) && (extentSize != null)) {
          boolean canScroll = true;
          if (view instanceof Scrollable) {
            canScroll = !((Scrollable)view).getScrollableTracksViewportHeight();
          }
          if (canScroll && viewSize.height > extentSize.height && considerVsbWidth) {
            prefWidth += vsb.getPreferredSize().width;
          }
        }
      }

      if ((hsb != null) && (hsbPolicy != HORIZONTAL_SCROLLBAR_NEVER)) {
        boolean considerHsbHeight = hsb.isOpaque() || isAlwaysOpaque(view);
        if (hsbPolicy == HORIZONTAL_SCROLLBAR_ALWAYS && considerHsbHeight) {
          prefHeight += hsb.getPreferredSize().height;
        }
        else if ((viewSize != null) && (extentSize != null)) {
          boolean canScroll = true;
          if (view instanceof Scrollable) {
            canScroll = !((Scrollable)view).getScrollableTracksViewportWidth();
          }
          if (canScroll && (viewSize.width > extentSize.width) && considerHsbHeight) {
            prefHeight += hsb.getPreferredSize().height;
          }
        }
      }

      return new Dimension(prefWidth, prefHeight);
    }

    private static boolean isAlwaysOpaque(Component view) {
      return !SystemInfo.isMac && ScrollSettings.isNotSupportedYet(view);
    }

    private static void updateCornerBounds(Rectangle bounds, int x, int y) {
      bounds.width = Math.abs(bounds.x - x);
      bounds.height = Math.abs(bounds.y - y);
      bounds.x = Math.min(bounds.x, x);
      bounds.y = Math.min(bounds.y, y);
    }

    private static void fixComponentZOrder(Component component, int index) {
      if (component != null) {
        Container parent = component.getParent();
        synchronized (parent.getTreeLock()) {
          if (index < 0) index += parent.getComponentCount();
          parent.setComponentZOrder(component, index);
        }
      }
    }

    private void adjustForVSB(Rectangle bounds, Insets insets, Rectangle vsbBounds, boolean vsbOpaque, boolean vsbOnLeft) {
      vsbBounds.width = !vsb.isEnabled() ? 0 : min(bounds.width, vsb.getPreferredSize().width);
      if (vsbOnLeft) {
        vsbBounds.x = bounds.x - insets.left/* + vsbBounds.width*/;
        if (vsbOpaque) bounds.x += vsbBounds.width;
      }
      else {
        vsbBounds.x = bounds.x + bounds.width + insets.right - vsbBounds.width;
      }
      if (vsbOpaque) bounds.width -= vsbBounds.width;
    }

    private void adjustForHSB(Rectangle bounds, Insets insets, Rectangle hsbBounds, boolean hsbOpaque, boolean hsbOnTop) {
      hsbBounds.height = !hsb.isEnabled() ? 0 : min(bounds.height, hsb.getPreferredSize().height);
      if (hsbOnTop) {
        hsbBounds.y = bounds.y - insets.top/* + hsbBounds.height*/;
        if (hsbOpaque) bounds.y += hsbBounds.height;
      }
      else {
        hsbBounds.y = bounds.y + bounds.height + insets.bottom - hsbBounds.height;
      }
      if (hsbOpaque) bounds.height -= hsbBounds.height;
    }

    private static int min(int one, int two) {
      return Math.max(0, Math.min(one, two));
    }
  }

  /**
   * Indicates whether the specified event is not consumed and does not have unexpected modifiers.
   *
   * @param event a mouse wheel event to check for validity
   * @return {@code true} if the specified event is valid, {@code false} otherwise
   */
  public static boolean isScrollEvent(@NotNull MouseWheelEvent event) {
    // event should not be consumed already
    if (event.isConsumed()) return false;
    // any rotation expected (forward or backward)
    boolean ignore = event.getWheelRotation() == 0;
    if (ignore && (ScrollSettings.isPixelPerfectEnabled() || ScrollSettings.isHighPrecisionEnabled())) {
      double rotation = event.getPreciseWheelRotation();
      ignore = rotation == 0.0D || !Double.isFinite(rotation);
    }
    return !ignore && 0 == (SCROLL_MODIFIERS & event.getModifiers());
  }

  private static final int SCROLL_MODIFIERS = // event modifiers allowed during scrolling
    ~InputEvent.SHIFT_MASK & ~InputEvent.SHIFT_DOWN_MASK & // for horizontal scrolling
    ~InputEvent.BUTTON1_MASK & ~InputEvent.BUTTON1_DOWN_MASK; // for selection
}
