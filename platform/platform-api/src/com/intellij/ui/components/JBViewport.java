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
    if (myZoomer != null && myZoomer.isActive()) {
      myZoomer.paint(g);
    }
    else {
      super.paint(g);

      if (myEmptyText != null) {
        myEmptyText.paint(this, g);
      }
    }
  }

  @Nullable
  @Override
  public Magnificator getMagnificator() {
    JComponent view = (JComponent)getView();
    return view != null ? (Magnificator)view.getClientProperty(Magnificator.CLIENT_PROPERTY_KEY) : null;
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
}
