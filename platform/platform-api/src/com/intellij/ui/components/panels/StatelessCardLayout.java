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
package com.intellij.ui.components.panels;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;

/**
 * This class is intended to lay out only one visible component.
 * It takes into account not only a minimum size of a component,
 * but also its maximum size.  This class uses a component alignment
 * to lay out a component whose maximum size less than a container size.
 *
 * @author Sergey.Malenkov
 * @see Component#getAlignmentX
 * @see Component#getAlignmentY
 */
public class StatelessCardLayout implements LayoutManager2 {
  public static final StatelessCardLayout INSTANCE = new StatelessCardLayout();

  /**
   * Creates a simple panel with this layout manager and the specified component.
   *
   * @param component a component to add if necessary
   * @return new panel with this layout manager and the specified component
   * @see Wrapper
   */
  @NotNull
  public static JPanel wrap(Component component) {
    JPanel panel = new JPanel(INSTANCE);
    if (component != null) panel.add(component);
    panel.setOpaque(false);
    return panel;
  }

  /**
   * Shows the specified component if necessary and
   * hides other components in the same container.
   *
   * @param visible a component to show
   */
  public static void show(Component visible) {
    if (visible != null && !visible.isVisible()) {
      Container container = visible.getParent();
      if (container != null) {
        synchronized (container.getTreeLock()) {
          for (Component component : container.getComponents()) {
            component.setVisible(component == visible);
          }
        }
      }
    }
  }

  private static Component getComponent(Container container) {
    synchronized (container.getTreeLock()) {
      for (Component component : container.getComponents()) {
        if (component.isVisible()) return component;
      }
    }
    return null;
  }

  private static Dimension getSize(Container container, Dimension size) {
    Insets insets = container.getInsets();
    if (insets != null) {
      int width = size.width + insets.left + insets.right;
      if (size.width < width) size.width = width;
      int height = size.height + insets.top + insets.bottom;
      if (size.height < height) size.height = height;
    }
    return size;
  }

  @Override
  public void addLayoutComponent(String name, Component component) {
    addLayoutComponent(component, name);
  }

  @Override
  public void addLayoutComponent(Component component, Object constraints) {
    if (constraints != null) throw new IllegalArgumentException("unsupported constraints: " + constraints);
    Container container = component.getParent();
    component.setVisible(container != null && 0 < container.getComponentCount());
  }

  @Override
  public void removeLayoutComponent(Component component) {
  }

  @Override
  public void invalidateLayout(Container container) {
  }

  @Override
  public float getLayoutAlignmentX(Container container) {
    Component component = getComponent(container);
    return component != null ? component.getAlignmentX() : Component.CENTER_ALIGNMENT;
  }

  @Override
  public float getLayoutAlignmentY(Container container) {
    Component component = getComponent(container);
    return component != null ? component.getAlignmentY() : Component.CENTER_ALIGNMENT;
  }

  @Override
  public Dimension preferredLayoutSize(Container container) {
    Component component = getComponent(container);
    return getSize(container, component != null ? component.getPreferredSize() : new Dimension());
  }

  @Override
  public Dimension minimumLayoutSize(Container container) {
    Component component = getComponent(container);
    return getSize(container, component != null ? component.getMinimumSize() : new Dimension());
  }

  @Override
  public Dimension maximumLayoutSize(Container container) {
    Component component = getComponent(container);
    return getSize(container, component != null ? component.getMaximumSize() : new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
  }

  @Override
  public void layoutContainer(Container container) {
    synchronized (container.getTreeLock()) {
      boolean logged = false;
      boolean updated = false;
      for (Component component : container.getComponents()) {
        if (component.isVisible()) {
          if (!updated) {
            updated = true;
            Dimension min = component.getMinimumSize();
            Dimension max = component.getMaximumSize();
            if (max.height < min.height) max.height = min.height;

            Rectangle bounds = new Rectangle(container.getWidth(), container.getHeight());
            JBInsets.removeFrom(bounds, container.getInsets());

            if (max.width < min.width) max.width = min.width;
            if (bounds.width > max.width) {
              bounds.x += (int)(.5f + (bounds.width - max.width) * component.getAlignmentX());
              bounds.width = max.width;
            }
            else if (bounds.width < min.width) {
              bounds.x += (int)(.5f + (bounds.width - min.width) * component.getAlignmentX());
              bounds.width = min.width;
            }
            if (max.height < min.height) max.height = min.height;
            if (bounds.height > max.height) {
              bounds.y += (int)(.5f + (bounds.height - max.height) * component.getAlignmentY());
              bounds.height = max.height;
            }
            else if (bounds.height < min.height) {
              bounds.y += (int)(.5f + (bounds.height - min.height) * component.getAlignmentY());
              bounds.height = min.height;
            }
            component.setBounds(bounds);
          }
          else {
            component.setVisible(false);
            if (!logged) {
              logged = true;
              Logger.getInstance(StatelessCardLayout.class).warn("too many visible components");
            }
          }
        }
      }
    }
  }
}
