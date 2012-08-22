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

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

public class JBViewport extends JViewport implements ZoomableViewport {
  private StatusText myEmptyText;
  private ZoomingDelegate myZoomer;

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
