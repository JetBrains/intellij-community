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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.Gray;
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
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.*;

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
  public static final RegionPainter<Float> TRACK_PAINTER = new AlphaPainter(.0f, .1f, Gray.x80);

  @Deprecated
  public static final RegionPainter<Float> TRACK_DARK_PAINTER = new AlphaPainter(.0f, .1f, Gray.x80);

  @Deprecated
  public static final RegionPainter<Float> THUMB_PAINTER = new ProtectedPainter(new SubtractThumbPainter(.20f, .15f, Gray.x80, Gray.x91),
                                                                                new ThumbPainter(.7f, .2f, Gray.x99, Gray.x8C));
  @Deprecated
  public static final RegionPainter<Float> THUMB_DARK_PAINTER = new ThumbPainter(.35f, .25f, Gray.x80, Gray.x94);

  @Deprecated
  public static final RegionPainter<Float> MAC_THUMB_PAINTER = new RoundThumbPainter(2, .5f, .4f, Gray.x99);

  @Deprecated
  public static final RegionPainter<Float> MAC_THUMB_DARK_PAINTER = new RoundThumbPainter(2, .15f, .25f, Gray.x80);

  private int myViewportBorderWidth = -1;
  private boolean myHasOverlayScrollbars;
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
    if (!myBackgroundRequested && EventQueue.isDispatchThread() && Registry.is("ide.scroll.background.auto")) {
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
    setLayout(Registry.is("ide.scroll.new.layout") ? new Layout() : new ScrollPaneLayout());

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
  }

  @Override
  public boolean isOptimizedDrawingEnabled() {
    if (getLayout() instanceof Layout) {
      return isOptimizedDrawingEnabledFor(getVerticalScrollBar()) &&
             isOptimizedDrawingEnabledFor(getHorizontalScrollBar());
    }
    return !myHasOverlayScrollbars;
  }

  /**
   * Returns {@code false} for visible translucent scroll bars, or {@code true} otherwise.
   * It is needed to repaint translucent scroll bars on viewport repainting.
   */
  private static boolean isOptimizedDrawingEnabledFor(JScrollBar bar) {
    return bar == null || bar.isOpaque() || !bar.isVisible();
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
    return new MyScrollBar(Adjustable.VERTICAL);
  }

  @NotNull
  @Override
  public JScrollBar createHorizontalScrollBar() {
    return new MyScrollBar(Adjustable.HORIZONTAL);
  }

  @Override
  protected JViewport createViewport() {
    return new JBViewport();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void layout() {
    LayoutManager layout = getLayout();
    ScrollPaneLayout scrollLayout = layout instanceof ScrollPaneLayout ? (ScrollPaneLayout)layout : null;

    // Now we let JScrollPane layout everything as necessary
    super.layout();

    if (layout instanceof Layout) return;

    if (scrollLayout != null) {
      // Now it's time to jump in and expand the viewport so it fits the whole area
      // (taking into consideration corners, headers and other stuff).
      myHasOverlayScrollbars = relayoutScrollbars(
        this, scrollLayout,
        myHasOverlayScrollbars // If last time we did relayouting, we should restore it back.
      );
    }
    else {
      myHasOverlayScrollbars = false;
    }
  }

  private boolean relayoutScrollbars(@NotNull JComponent container, @NotNull ScrollPaneLayout layout, boolean forceRelayout) {
    JViewport viewport = layout.getViewport();
    if (viewport == null) return false;

    JScrollBar vsb = layout.getVerticalScrollBar();
    JScrollBar hsb = layout.getHorizontalScrollBar();
    JViewport colHead = layout.getColumnHeader();
    JViewport rowHead = layout.getRowHeader();

    Rectangle viewportBounds = viewport.getBounds();

    boolean extendViewportUnderVScrollbar = vsb != null && shouldExtendViewportUnderScrollbar(vsb);
    boolean extendViewportUnderHScrollbar = hsb != null && shouldExtendViewportUnderScrollbar(hsb);
    boolean hasOverlayScrollbars = extendViewportUnderVScrollbar || extendViewportUnderHScrollbar;

    if (!hasOverlayScrollbars && !forceRelayout) return false;

    container.setComponentZOrder(viewport, container.getComponentCount() - 1);
    if (vsb != null) container.setComponentZOrder(vsb, 0);
    if (hsb != null) container.setComponentZOrder(hsb, 0);

    if (extendViewportUnderVScrollbar) {
      int x2 = Math.max(vsb.getX() + vsb.getWidth(), viewportBounds.x + viewportBounds.width);
      viewportBounds.x = Math.min(viewportBounds.x, vsb.getX());
      viewportBounds.width = x2 - viewportBounds.x;
    }
    if (extendViewportUnderHScrollbar) {
      int y2 = Math.max(hsb.getY() + hsb.getHeight(), viewportBounds.y + viewportBounds.height);
      viewportBounds.y = Math.min(viewportBounds.y, hsb.getY());
      viewportBounds.height = y2 - viewportBounds.y;
    }

    if (extendViewportUnderVScrollbar) {
      if (hsb != null) {
        Rectangle scrollbarBounds = hsb.getBounds();
        scrollbarBounds.width = viewportBounds.x + viewportBounds.width - scrollbarBounds.x;
        hsb.setBounds(scrollbarBounds);
      }
      if (colHead != null) {
        Rectangle headerBounds = colHead.getBounds();
        headerBounds.width = viewportBounds.width;
        colHead.setBounds(headerBounds);
      }
      hideFromView(layout.getCorner(UPPER_RIGHT_CORNER));
      hideFromView(layout.getCorner(LOWER_RIGHT_CORNER));
    }
    if (extendViewportUnderHScrollbar) {
      if (vsb != null) {
        Rectangle scrollbarBounds = vsb.getBounds();
        scrollbarBounds.height = viewportBounds.y + viewportBounds.height - scrollbarBounds.y;
        vsb.setBounds(scrollbarBounds);
      }
      if (rowHead != null) {
        Rectangle headerBounds = rowHead.getBounds();
        headerBounds.height = viewportBounds.height;
        rowHead.setBounds(headerBounds);
      }

      hideFromView(layout.getCorner(LOWER_LEFT_CORNER));
      hideFromView(layout.getCorner(LOWER_RIGHT_CORNER));
    }

    viewport.setBounds(viewportBounds);

    return hasOverlayScrollbars;
  }

  private boolean shouldExtendViewportUnderScrollbar(@Nullable JScrollBar scrollbar) {
    if (scrollbar == null || !scrollbar.isVisible()) return false;
    return isOverlaidScrollbar(scrollbar);
  }

  protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
    if (!ButtonlessScrollBarUI.isMacOverlayScrollbarSupported()) return false;

    ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
    return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
  }

  private static void hideFromView(Component component) {
    if (component == null) return;
    component.setBounds(-10, -10, 1, 1);
  }

  private class MyScrollBar extends ScrollBar implements IdeGlassPane.TopComponent {
    public MyScrollBar(int orientation) {
      super(orientation);
    }

    @Override
    public void updateUI() {
      ScrollBarUI ui = getUI();
      if (ui instanceof DefaultScrollBarUI) return;
      setUI(DefaultScrollBarUI.createUI(this));
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }
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
    }
    return true;
  }

  private static class Corner extends JPanel {
    private final String myPos;

    public Corner(String pos) {
      myPos = pos;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(ButtonlessScrollBarUI.getTrackBackgroundDefault());
      g.fillRect(0, 0, getWidth(), getHeight());

      g.setColor(ButtonlessScrollBarUI.getTrackBorderColorDefault());

      int x2 = getWidth() - 1;
      int y2 = getHeight() - 1;

      if (myPos == UPPER_LEFT_CORNER || myPos == UPPER_RIGHT_CORNER) {
        g.drawLine(0, y2, x2, y2);
      }
      if (myPos == LOWER_LEFT_CORNER || myPos == LOWER_RIGHT_CORNER) {
        g.drawLine(0, 0, x2, 0);
      }

      if (myPos == UPPER_LEFT_CORNER || myPos == LOWER_LEFT_CORNER) {
        g.drawLine(x2, 0, x2, y2);
      }

      if (myPos == UPPER_RIGHT_CORNER || myPos == LOWER_RIGHT_CORNER) {
        g.drawLine(0, 0, 0, y2);
      }
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
      }
      return null;
    }
  }

  /**
   * ScrollPaneLayout implementation that supports
   * ScrollBar flipping and non-opaque ScrollBars.
   */
  private static class Layout extends ScrollPaneLayout {
    private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);

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
      // If there's a vertical scroll bar and we need one, allocate space for it.
      // A vertical scroll bar is considered to be fixed width, arbitrary height.
      boolean vsbOpaque = false;
      boolean vsbNeeded = false;
      int vsbPolicy = pane.getVerticalScrollBarPolicy();
      if (!isEmpty && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
        vsbNeeded = vsbPolicy == VERTICAL_SCROLLBAR_ALWAYS
                    || !viewTracksViewportHeight && viewPreferredSize.height > viewportExtentSize.height;
      }
      Rectangle vsbBounds = new Rectangle(0, bounds.y - insets.top, 0, 0);
      if (vsb != null) {
        if (!SystemInfo.isMac && view instanceof JTable) vsb.setOpaque(true);
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
                    || !viewTracksViewportWidth && viewPreferredSize.width > viewportExtentSize.width;
      }
      Rectangle hsbBounds = new Rectangle(bounds.x - insets.left, 0, 0, 0);
      if (hsb != null) {
        if (!SystemInfo.isMac && view instanceof JTable) hsb.setOpaque(true);
        hsbOpaque = hsb.isOpaque();
        if (hsbNeeded) {
          adjustForHSB(bounds, insets, hsbBounds, hsbOpaque, hsbOnTop);
          if (hsbOpaque && viewport != null) {
            // If we added the horizontal scrollbar and reduced the vertical space
            // we may have to add the vertical scrollbar, if that hasn't been done so already.
            if (vsb != null && !vsbNeeded && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
              viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
              vsbNeeded = viewPreferredSize.height > viewportExtentSize.height;
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
            boolean vsbNeededNew = !viewTracksViewportHeight && viewPreferredSize.height > viewportExtentSize.height;
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
            boolean hsbNeededNew = !viewTracksViewportWidth && viewPreferredSize.width > viewportExtentSize.width;
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
                vsbNeeded = viewPreferredSize.height > viewportExtentSize.height;
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
      if (rowHead != null) {
        rowHead.setBounds(rowHeadBounds);
        rowHead.putClientProperty(Alignment.class, vsbOnLeft ? Alignment.RIGHT : Alignment.LEFT);
      }
      // Set the bounds of the column header.
      colHeadBounds.x = bounds.x - insets.left;
      colHeadBounds.width = bounds.width + insets.left + insets.right;
      if (colHead != null) {
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
          if (vsbOpaque && colHead != null && UIManager.getBoolean("ScrollPane.fillUpperCorner")) {
            if ((vsbOnLeft ? upperLeft : upperRight) == null) {
              // This is used primarily for GTK L&F, which needs to extend
              // the vertical scrollbar to fill the upper corner near the column header.
              // Note that we skip this step (and use the default behavior)
              // if the user has set a custom corner component.
              if (!hsbOnTop) vsbBounds.y -= colHeadBounds.height;
              vsbBounds.height += colHeadBounds.height;
            }
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
          if (hsbOpaque && rowHead != null && UIManager.getBoolean("ScrollPane.fillLowerCorner")) {
            if ((vsbOnLeft ? lowerRight : lowerLeft) == null) {
              // This is used primarily for GTK L&F, which needs to extend
              // the horizontal scrollbar to fill the lower corner near the row header.
              // Note that we skip this step (and use the default behavior)
              // if the user has set a custom corner component.
              if (!vsbOnLeft) hsbBounds.x -= rowHeadBounds.width;
              hsbBounds.width += rowHeadBounds.width;
            }
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
      if (lowerLeft != null) {
        lowerLeft.setBounds(vsbOnLeft ? vsbBounds.x : rowHeadBounds.x,
                            hsbOnTop ? colHeadBounds.y : hsbBounds.y,
                            vsbOnLeft ? vsbBounds.width : rowHeadBounds.width,
                            hsbOnTop ? colHeadBounds.height : hsbBounds.height);
      }
      if (lowerRight != null) {
        lowerRight.setBounds(vsbOnLeft ? rowHeadBounds.x : vsbBounds.x,
                             hsbOnTop ? colHeadBounds.y : hsbBounds.y,
                             vsbOnLeft ? rowHeadBounds.width : vsbBounds.width,
                             hsbOnTop ? colHeadBounds.height : hsbBounds.height);
      }
      if (upperLeft != null) {
        upperLeft.setBounds(vsbOnLeft ? vsbBounds.x : rowHeadBounds.x,
                            hsbOnTop ? hsbBounds.y : colHeadBounds.y,
                            vsbOnLeft ? vsbBounds.width : rowHeadBounds.width,
                            hsbOnTop ? hsbBounds.height : colHeadBounds.height);
      }
      if (upperRight != null) {
        upperRight.setBounds(vsbOnLeft ? rowHeadBounds.x : vsbBounds.x,
                             hsbOnTop ? hsbBounds.y : colHeadBounds.y,
                             vsbOnLeft ? rowHeadBounds.width : vsbBounds.width,
                             hsbOnTop ? hsbBounds.height : colHeadBounds.height);
      }
      if (!vsbOpaque && vsbNeeded || !hsbOpaque && hsbNeeded) {
        fixComponentZOrder(vsb, 0);
        fixComponentZOrder(viewport, -1);
      }
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
      vsbBounds.width = min(bounds.width, vsb.getPreferredSize().width);
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
      hsbBounds.height = min(bounds.height, hsb.getPreferredSize().height);
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

  private static class ProtectedPainter implements RegionPainter<Float> {
    private RegionPainter<Float> myPainter;
    private RegionPainter<Float> myFallback;

    public ProtectedPainter(RegionPainter<Float> painter, RegionPainter<Float> fallback) {
      myPainter = painter;
      myFallback = fallback;
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
      RegionPainter<Float> painter = myFallback;
      if (myPainter != null) {
        try {
          myPainter.paint(g, x, y, width, height, value);
          return;
        }
        catch (Throwable exception) {
          // do not try to use myPainter again on other systems
          if (!SystemInfo.isWindows) myPainter = null;
        }
      }
      if (painter != null) {
        painter.paint(g, x, y, width, height, value);
      }
    }
  }

  private static class AlphaPainter implements RegionPainter<Float> {
    private final float myBase;
    private final float myDelta;
    private final Color myFillColor;

    private AlphaPainter(float base, float delta, Color fill) {
      myBase = base;
      myDelta = delta;
      myFillColor = fill;
    }

    Composite newComposite(float alpha) {
      return AlphaComposite.SrcOver.derive(alpha);
    }

    void paint(Graphics2D g, int x, int y, int width, int height) {
      g.fillRect(x, y, width, height);
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
      if (value != null) {
        float alpha = myBase + myDelta * value;
        if (alpha > 0) {
          Composite old = g.getComposite();
          g.setComposite(alpha < 1
                         ? newComposite(alpha)
                         : AlphaComposite.SrcOver);
          g.setColor(myFillColor);
          paint(g, x, y, width, height);
          g.setComposite(old);
        }
      }
    }
  }

  private static class RoundThumbPainter extends AlphaPainter {
    private final int myBorder;

    private RoundThumbPainter(int border, float base, float delta, Color fill) {
      super(base, delta, fill);
      myBorder = border;
    }

    @Override
    void paint(Graphics2D g, int x, int y, int width, int height) {
      Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      width -= myBorder + myBorder;
      height -= myBorder + myBorder;

      int arc = Math.min(width, height);
      g.fillRoundRect(x + myBorder, y + myBorder, width, height, arc, arc);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }
  }

  private static class ThumbPainter extends AlphaPainter {
    private final Color myDrawColor;

    private ThumbPainter(float base, float delta, Color fill, Color draw) {
      super(base, delta, fill);
      myDrawColor = draw;
    }

    @Override
    void paint(Graphics2D g, int x, int y, int width, int height) {
      super.paint(g, x + 1, y + 1, width - 2, height - 2);
      g.setColor(myDrawColor);
      if (Registry.is("ide.scroll.thumb.border.rounded")) {
        g.drawLine(x + 1, y, x + width - 2, y);
        g.drawLine(x + 1, y + height - 1, x + width - 2, y + height - 1);
        g.drawLine(x, y + 1, x, y + height - 2);
        g.drawLine(x + width - 1, y + 1, x + width - 1, y + height - 2);
      }
      else {
        g.drawRect(x, y, width - 1, height - 1);
      }
    }
  }

  private static class SubtractThumbPainter extends ThumbPainter {
    public SubtractThumbPainter(float base, float delta, Color fill, Color draw) {
      super(base, delta, fill, draw);
    }

    @Override
    Composite newComposite(float alpha) {
      return new SubtractComposite(alpha);
    }
  }

  private static class SubtractComposite implements Composite, CompositeContext {
    private final float myAlpha;

    private SubtractComposite(float alpha) {
      myAlpha = alpha;
    }

    private int subtract(int newValue, int oldValue) {
      float value = (oldValue & 0xFF) - (newValue & 0xFF) * myAlpha;
      return value <= 0 ? 0 : (int)value;
    }

    @Override
    public CompositeContext createContext(ColorModel src, ColorModel dst, RenderingHints hints) {
      return isValid(src) && isValid(dst) ? this : AlphaComposite.SrcOver.derive(myAlpha).createContext(src, dst, hints);
    }

    private static boolean isValid(ColorModel model) {
      if (model instanceof DirectColorModel && DataBuffer.TYPE_INT == model.getTransferType()) {
        DirectColorModel dcm = (DirectColorModel)model;
        if (0x00FF0000 == dcm.getRedMask() && 0x0000FF00 == dcm.getGreenMask() && 0x000000FF == dcm.getBlueMask()) {
          return 4 != dcm.getNumComponents() || 0xFF000000 == dcm.getAlphaMask();
        }
      }
      return false;
    }

    @Override
    public void compose(Raster srcIn, Raster dstIn, WritableRaster dstOut) {
      int width = Math.min(srcIn.getWidth(), dstIn.getWidth());
      int height = Math.min(srcIn.getHeight(), dstIn.getHeight());

      int[] srcPixels = new int[width];
      int[] dstPixels = new int[width];

      for (int y = 0; y < height; y++) {
        srcIn.getDataElements(0, y, width, 1, srcPixels);
        dstIn.getDataElements(0, y, width, 1, dstPixels);
        for (int x = 0; x < width; x++) {
          int src = srcPixels[x];
          int dst = dstPixels[x];
          int a = subtract(src >> 24, dst >> 24) << 24;
          int r = subtract(src >> 16, dst >> 16) << 16;
          int g = subtract(src >> 8, dst >> 8) << 8;
          int b = subtract(src, dst);
          dstPixels[x] = a | r | g | b;
        }
        dstOut.setDataElements(0, y, width, 1, dstPixels);
      }
    }

    @Override
    public void dispose() {
    }
  }
}
