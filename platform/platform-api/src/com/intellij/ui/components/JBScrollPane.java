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
package com.intellij.ui.components;

import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
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
import java.lang.reflect.Method;

public class JBScrollPane extends JScrollPane {
  private int myViewportBorderWidth = -1;
  private JLayeredPane myLayeredPane;

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
  public void setVerticalScrollBar(JScrollBar sb) {
    JScrollBar old = getVerticalScrollBar();
    super.setVerticalScrollBar(sb);
    if (myLayeredPane != null && old != null && old != sb) {
      myLayeredPane.remove(old);
    }
  }

  @Override
  public void setHorizontalScrollBar(JScrollBar sb) {
    JScrollBar old = getHorizontalScrollBar();
    super.setHorizontalScrollBar(sb);
    if (myLayeredPane != null && old != null && old != sb) {
      myLayeredPane.remove(old);
    }
  }

  private void init() {
    init(true);
  }
  
  private void init(boolean setupCorners) {
    myLayeredPane = new JLayeredPane() {
      @Override
      public void remove(int index) {
        Component c = getComponent(index);
        super.remove(index);
        JBScrollPane.this.getLayout().removeLayoutComponent(c);
      }

      @Override
      public void removeAll() {
        Component[] components = getComponents();
        super.removeAll();
        for (Component each : components) {
          JBScrollPane.this.getLayout().removeLayoutComponent(each);
        }
      }
    };

    if (setupCorners) {
      setupCorners();
    }

    myLayeredPane.setLayer(verticalScrollBar, JLayeredPane.PALETTE_LAYER);
    myLayeredPane.setLayer(horizontalScrollBar, JLayeredPane.PALETTE_LAYER);

    for (Component each : getComponents()) {
      if (each != myLayeredPane) {
        myLayeredPane.add(each);
      }
    }
    add(myLayeredPane);
    setLayout(new ScrollPaneLayout());
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
    super.layout();
  
    LayoutManager layout = getLayout();
    if (layout instanceof ScrollPaneLayout && myLayeredPane != null) {
      relayoutScrollbars(this, (ScrollPaneLayout)layout, myLayeredPane);
    }
  }

  private static void relayoutScrollbars(@NotNull JComponent container,
                                         @NotNull ScrollPaneLayout layout,
                                         @NotNull JLayeredPane layeredPane) {
    JViewport viewport = layout.getViewport();
    if (viewport == null) return;
    
    JScrollBar vsb = layout.getVerticalScrollBar();
    JScrollBar hsb = layout.getHorizontalScrollBar();
    JViewport colHead = layout.getColumnHeader();
    JViewport rowHead = layout.getRowHeader();

    Rectangle viewportBounds = viewport.getBounds();

    boolean extendsViewportUnderVScrollbar = vsb != null && shouldExtendViewportUnderScrollbar(vsb);
    boolean extendsViewportUnderHScrollbar = hsb != null && shouldExtendViewportUnderScrollbar(hsb);
    
    if (extendsViewportUnderVScrollbar) {
      viewportBounds.x = Math.min(viewportBounds.x, vsb.getX());
      viewportBounds.width = Math.max(viewportBounds.width, vsb.getX() + vsb.getWidth());
    }
    if (extendsViewportUnderHScrollbar) {
      viewportBounds.y = Math.min(viewportBounds.y, hsb.getY());
      viewportBounds.height = Math.max(viewportBounds.height, hsb.getY() + hsb.getHeight());
    }
 
    if (extendsViewportUnderVScrollbar) {
      if (hsb != null) {
        Rectangle scrollbarBounds = hsb.getBounds();
        scrollbarBounds.width = viewportBounds.width - scrollbarBounds.x;
        hsb.setBounds(scrollbarBounds);
      }
      if (colHead != null) {
        Rectangle headerBounds = colHead.getBounds();
        headerBounds.width = viewportBounds.width - headerBounds.x;
        colHead.setBounds(headerBounds);
      }
      hideFromView(layout.getCorner(UPPER_RIGHT_CORNER));
      hideFromView(layout.getCorner(LOWER_RIGHT_CORNER));
    }
    if (extendsViewportUnderHScrollbar) {
      if (vsb != null) {
        Rectangle scrollbarBounds = vsb.getBounds();
        scrollbarBounds.height = viewportBounds.height - scrollbarBounds.y;
        vsb.setBounds(scrollbarBounds);
      }
      if (rowHead != null) {
        Rectangle headerBounds = rowHead.getBounds();
        headerBounds.height = viewportBounds.height - headerBounds.y;
        rowHead.setBounds(headerBounds);
      }

      hideFromView(layout.getCorner(LOWER_LEFT_CORNER));
      hideFromView(layout.getCorner(LOWER_RIGHT_CORNER));
    }

    viewport.setBounds(viewportBounds);
    Insets insets = container.getInsets();
    if (insets == null) insets = new Insets(0, 0, 0, 0);
    layeredPane.setBounds(insets.left, insets.top, 
                          container.getWidth() - insets.right - insets.left, 
                          container.getHeight() - insets.top - insets.bottom);
  }

  private static boolean shouldExtendViewportUnderScrollbar(@Nullable JScrollBar scrollbar) {
    if (scrollbar == null || !scrollbar.isVisible()) return false;
    ScrollBarUI vsbUI = scrollbar.getUI();
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
          Method m = BasicScrollBarUI.class.getDeclaredMethod("getThumbBounds", ArrayUtil.EMPTY_CLASS_ARRAY);
          m.setAccessible(true);
          Rectangle rect = (Rectangle)m.invoke(bui);
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
      g.setColor(ButtonlessScrollBarUI.getTrackBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      g.setColor(ButtonlessScrollBarUI.getTrackBorderColor());

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
