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
          JScrollPane pane = getScrollPane(viewport);
          if (pane != null) {
            doLayout(pane, viewport, view);
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
    JComponent view = (JComponent)getView();
    return view != null ? UIUtil.getClientProperty(view, Magnificator.CLIENT_PROPERTY_KEY) : null;
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

  @Override
  public Color getBackground() {
    Component view = getView();
    return view != null && view.isBackgroundSet()
           ? view.getBackground()
           : super.getBackground();
  }

  @Override
  public Dimension getExtentSize() {
    Dimension size = super.getExtentSize();
    if (SystemInfo.isMac) return size;
    JScrollPane pane = getScrollPane(this);
    if (pane != null) {
      JScrollBar vsb = pane.getVerticalScrollBar();
      if (null != getAlignment(vsb)) {
        int width = vsb.getWidth();
        if (size.width > width) size.width -= width;
      }
      JScrollBar hsb = pane.getHorizontalScrollBar();
      if (null != getAlignment(hsb)) {
        int height = hsb.getHeight();
        if (size.height > height) size.height -= height;
      }
    }
    return size;
  }

  /**
   * Returns the alignment of the specified scroll bar
   * if and only if the specified scroll bar
   * is located over the corresponding viewport.
   *
   * @param bar the scroll bar to process
   * @return the scroll bar alignment or {@code null}
   */
  private static Alignment getAlignment(JScrollBar bar) {
    if (bar != null && bar.isVisible() && !bar.isOpaque()) {
      Object property = bar.getClientProperty(Alignment.class);
      if (property instanceof Alignment) return (Alignment)property;
    }
    return null;
  }

  /**
   * Returns the parent scroll pane of the specified viewport
   * if and only if the specified viewport is a main viewport.
   *
   * @param viewport the viewport to process
   * @return the parent scroll pane or {@code null}
   */
  private static JScrollPane getScrollPane(JViewport viewport) {
    Container parent = viewport.getParent();
    if (parent instanceof JScrollPane) {
      JScrollPane pane = (JScrollPane)parent;
      if (viewport == pane.getViewport()) return pane;
    }
    return null;
  }

  private static void doLayout(JScrollPane pane, JViewport viewport, Component view) {
    Dimension actualSize = viewport.getSize();
    Dimension extentSize = viewport.getExtentSize();
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
      // NOT SUPPORTED YET
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
        viewSize.width = actualSize.width;
      }
      if (ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER == pane.getVerticalScrollBarPolicy()) {
        viewPosition.y = 0;
        viewSize.height = actualSize.height;
      }
    }
    viewport.setViewPosition(viewPosition);
    viewport.setViewSize(viewSize);
  }
}
