/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

public class JBViewport extends JViewport implements ZoomableViewport {
  private static final ViewportLayout ourLayoutManager = new ViewportLayout() {

    @Override
    public void layoutContainer(Container parent) {
      if (parent instanceof JViewport && Registry.is("ide.scroll.new.layout")) {
        JViewport viewport = (JViewport)parent;
        Component view = viewport.getView();
        if (view != null) {
          Container grand = viewport.getParent();
          if (grand instanceof JScrollPane) {
            doLayout((JScrollPane)grand, viewport, view);
          }
          else {
            super.layoutContainer(parent);
          }
        }
        return;
      }
      JBViewport viewport = (JBViewport)parent;
      Component view = viewport.getView();
      JBScrollPane scrollPane = UIUtil.getParentOfType(JBScrollPane.class, parent);
      // do not force viewport size on editor component, e.g. EditorTextField and LanguageConsole
      if (view == null || scrollPane == null || view instanceof TypingTarget) {
        super.layoutContainer(parent);
        return;
      }

      Dimension size = doSuperLayoutContainer(viewport);

      Dimension visible = viewport.getExtentSize();
      if (scrollPane.getHorizontalScrollBarPolicy() == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
        size.width = visible.width;
      }
      if (scrollPane.getVerticalScrollBarPolicy() == ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER) {
        size.height = visible.height;
      }
      viewport.setViewSize(size);
    }

    private Dimension doSuperLayoutContainer(JBViewport viewport) {
      try {
        viewport.mySaveTempViewSize = true;
        super.layoutContainer(viewport);
      }
      finally {
        viewport.mySaveTempViewSize = false;
      }
      return viewport.myTempViewSize;
    }
  };

  private StatusText myEmptyText;
  private boolean myPaintingNow;

  private ZoomingDelegate myZoomer;

  private Dimension myTempViewSize;
  private boolean mySaveTempViewSize;
  private volatile boolean myBackgroundRequested; // avoid cyclic references

  public JBViewport() {
    addContainerListener(new ContainerListener() {
      @Override
      public void componentAdded(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          myEmptyText = ((ComponentWithEmptyText)child).getEmptyText();
          myEmptyText.attachTo(JBViewport.this, child);
        }
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          ((ComponentWithEmptyText)child).getEmptyText().attachTo(child);
          myEmptyText = null;
        }
      }
    });
  }

  @Override
  public Color getBackground() {
    Color color = super.getBackground();
    if (!myBackgroundRequested && EventQueue.isDispatchThread() && Registry.is("ide.scroll.background.auto")) {
      if (!isBackgroundSet() || color instanceof UIResource) {
        Component child = getView();
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

  @Override
  protected LayoutManager createLayoutManager() {
    return ourLayoutManager;
  }

  @Override
  public void setViewSize(Dimension newSize) {
    // only store newSize from ViewportLayout.layoutContainer
    // if we're going to fix it the next moment in our layoutContainer code
    if (mySaveTempViewSize) {
      myTempViewSize = newSize;
    }
    else {
      super.setViewSize(newSize);
    }
  }

  @Override
  public void paint(Graphics g) {
    myPaintingNow = true;
    if (myZoomer != null && myZoomer.isActive()) {
      myZoomer.paint(g);
    }
    else {
      super.paint(g);

      if (myEmptyText != null) {
        myEmptyText.paint(this, g);
      }
    }
    myPaintingNow = false;
  }

  @Nullable
  @Override
  public Magnificator getMagnificator() {
    return UIUtil.getClientProperty(getView(), Magnificator.CLIENT_PROPERTY_KEY);
  }

  @Override
  public void magnificationStarted(Point at) {
    myZoomer = new ZoomingDelegate((JComponent)getView(), this);
    myZoomer.magnificationStarted(at);
  }

  @Override
  public void magnificationFinished(double magnification) {
    myZoomer.magnificationFinished(magnification);
    myZoomer = null;
  }

  @Override
  public void magnify(double magnification) {
    myZoomer.magnify(magnification);
  }

  public boolean isPaintingNow() {
    return myPaintingNow;
  }

  /**
   * Returns the alignment of the specified scroll bar
   * if and only if the specified scroll bar
   * is located over the main viewport.
   *
   * @param bar the scroll bar to process
   * @return the scroll bar alignment or {@code null}
   */
  private static Alignment getAlignment(JScrollBar bar) {
    if (bar != null && bar.isVisible() && !bar.isOpaque()) {
      return UIUtil.getClientProperty(bar, Alignment.class);
    }
    return null;
  }

  private static void doLayout(JScrollPane pane, JViewport viewport, Component view) {
    updateBorder(view);

    Dimension actualSize = viewport.getSize();
    Dimension extentSize = viewport.toViewCoordinates(actualSize);
    Dimension viewPreferredSize = view.getPreferredSize();
    Dimension viewSize = new Dimension(viewPreferredSize);
    Point viewPosition = viewport.getViewPosition();

    Scrollable scrollable = null;
    if (view instanceof Scrollable) {
      scrollable = (Scrollable)view;
      if (scrollable.getScrollableTracksViewportWidth()) viewSize.width = actualSize.width;
      if (scrollable.getScrollableTracksViewportHeight()) viewSize.height = actualSize.height;
    }
    // If the new viewport size would leave empty space to the right of the view,
    // right justify the view or left justify the view
    // when the width of the view is smaller than the container.
    int maxX = viewSize.width - extentSize.width;
    if (scrollable == null || pane.getComponentOrientation().isLeftToRight()) {
      if (viewPosition.x > maxX) {
        viewPosition.x = Math.max(0, maxX);
      }
    }
    else {
      viewPosition.x = maxX < 0 ? maxX : Math.max(0, Math.min(maxX, viewPosition.x));
    }
    // If the new viewport size would leave empty space below the view,
    // bottom justify the view or top justify the view
    // when the height of the view is smaller than the container.
    int maxY = viewSize.height - extentSize.height;
    if (viewPosition.y > maxY) {
      viewPosition.y = Math.max(0, maxY);
    }
    // If we haven't been advised about how the viewports size should change wrt to the viewport,
    // i.e. if the view isn't an instance of Scrollable, then adjust the views size as follows.
    if (scrollable == null) {
      // If the origin of the view is showing and the viewport is bigger than the views preferred size,
      // then make the view the same size as the viewport.
      if (viewPosition.x == 0 && actualSize.width > viewPreferredSize.width) viewSize.width = actualSize.width;
      if (viewPosition.y == 0 && actualSize.height > viewPreferredSize.height) viewSize.height = actualSize.height;
    }
    // do not force viewport size on editor component, e.g. EditorTextField and LanguageConsole
    if (!(view instanceof TypingTarget)) {
      if (ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER == pane.getHorizontalScrollBarPolicy()) {
        viewPosition.x = 0;
        viewSize.width = extentSize.width;
      }
      if (ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER == pane.getVerticalScrollBarPolicy()) {
        viewPosition.y = 0;
        viewSize.height = extentSize.height;
      }
    }
    viewport.setViewPosition(viewPosition);
    viewport.setViewSize(viewSize);
  }

  private static void updateBorder(Component view) {
    if (view instanceof JTable) return; // tables are not supported yet
    if (view instanceof JComponent) {
      JComponent component = (JComponent)view;
      Border border = component.getBorder();
      if (border instanceof ViewBorder) return; // already set
      component.setBorder(border == null || border instanceof UIResource
                          ? new ResourceViewBorder(border)
                          : new ViewBorder(border));
    }
  }

  /**
   * This border is used to add additional space for a view
   * and can be changed on UI update.
   */
  private static class ResourceViewBorder extends ViewBorder implements UIResource {
    ResourceViewBorder(Border border) {
      super(border);
    }
  }

  /**
   * This border is used to add additional space for a view.
   */
  private static class ViewBorder extends AbstractBorder {
    private final Insets myInsets = new Insets(0, 0, 0, 0);
    private final Border myBorder;

    ViewBorder(Border border) {
      myBorder = border;
    }

    @Override
    public Insets getBorderInsets(Component view, Insets insets) {
      if (insets == null) {
        insets = new Insets(0, 0, 0, 0);
      }
      else {
        insets.set(0, 0, 0, 0);
      }
      if (myBorder != null) {
        Insets inner = myBorder.getBorderInsets(view);
        if (inner != null) insets.set(inner.top, inner.left, inner.bottom, inner.right);
      }
      if (view instanceof JComponent) {
        addViewInsets((JComponent)view, insets);
      }
      if (!myInsets.equals(insets)) {
        myInsets.set(insets.top, insets.left, insets.bottom, insets.right);
        if (view instanceof JComponent) {
          JComponent component = (JComponent)view;
          if (component instanceof JTree) {
            // invalidate cached preferred size
            JTree tree = (JTree)component;
            TreeUI ui = tree.getUI();
            if (ui instanceof BasicTreeUI) {
              BasicTreeUI basic = (BasicTreeUI)ui;
              basic.setLeftChildIndent(basic.getLeftChildIndent());
            }
          }
          component.revalidate();
        }
        else {
          view.invalidate();
          view.repaint();
        }
      }
      return insets;
    }

    @Override
    public void paintBorder(Component view, Graphics g, int x, int y, int width, int height) {
      if (myBorder != null) {
        // additional insets are used inside a custom border
        myBorder.paintBorder(view, g, x, y, width, height);
      }
    }

    private void addViewInsets(JComponent view, Insets insets) {
      if (this == view.getBorder()) {
        Container parent = view.getParent();
        if (parent instanceof JViewport) {
          JViewport viewport = (JViewport)parent;
          Container grand = viewport.getParent();
          if (grand instanceof JScrollPane) {
            JScrollPane pane = (JScrollPane)grand;
            // calculate empty border under vertical scroll bar
            if (viewport == pane.getViewport() || viewport == pane.getColumnHeader()) {
              JScrollBar vsb = pane.getVerticalScrollBar();
              Alignment va = getAlignment(vsb);
              if (va == Alignment.LEFT) {
                insets.left += vsb.getWidth();
              }
              else if (va == Alignment.RIGHT && !SystemInfo.isMac) {
                insets.right += vsb.getWidth();
              }
            }
            // calculate empty border under horizontal scroll bar
            if (viewport == pane.getViewport() || viewport == pane.getRowHeader()) {
              JScrollBar hsb = pane.getHorizontalScrollBar();
              Alignment ha = getAlignment(hsb);
              if (ha == Alignment.TOP) {
                insets.top += hsb.getHeight();
              }
              else if (ha == Alignment.BOTTOM && !SystemInfo.isMac) {
                insets.bottom += hsb.getHeight();
              }
            }
          }
        }
      }
    }
  }
}
