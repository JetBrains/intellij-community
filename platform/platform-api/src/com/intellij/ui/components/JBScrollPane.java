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
package com.intellij.ui.components;

import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JBScrollPane extends JScrollPane {
  private int myViewportBorderWidth = -1;
  private boolean myHasOverlayScrollbars;

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
    setLayout(new ScrollPaneLayout());
 
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
    return !myHasOverlayScrollbars;
  }

  private void updateViewportBorder() {
    setViewportBorder(new ViewportBorder(myViewportBorderWidth >= 0 ? myViewportBorderWidth : 1));
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
      setUI(ButtonlessScrollBarUI.createNormal());
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

      JViewport vp = ((JScrollPane)c).getViewport();
      if (vp == null) return;

      Component view = vp.getView();
      if (view == null) return;

      lineColor = view.getBackground();
    }
  }
}
