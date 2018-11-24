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
package com.intellij.ui;

import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

public class ExpandedItemRendererComponentWrapper extends JComponent {
  JComponent owner;

  /**
   * @deprecated use {@link #wrap(Component)}} instead to create an instance
   */
  public ExpandedItemRendererComponentWrapper(@NotNull final Component rendererComponent) {
    add(rendererComponent);
    setOpaque(false);
    setLayout(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        return rendererComponent.getPreferredSize();
      }

      @Override
      public void layoutContainer(Container parent) {
        Dimension size = parent.getSize();
        if (owner != null) {
          Rectangle visible = owner.getVisibleRect();
          size.width = visible.x + visible.width;
        }
        Insets i = parent.getInsets();
        Dimension pref = rendererComponent.getPreferredSize();
        rendererComponent.setBounds(i.left, i.top, Math.max(pref.width, size.width - i.left - i.right), size.height - i.top - i.bottom);
      }
    });
  }
  private ExpandedItemRendererComponentWrapper() {}

  public static ExpandedItemRendererComponentWrapper wrap(@NotNull Component rendererComponent) {
    if (rendererComponent instanceof Accessible) {
      return new MyAccessibleComponent(rendererComponent, (Accessible)rendererComponent);
    }
    return new ExpandedItemRendererComponentWrapper(rendererComponent);
  }

  private static class MyAccessibleComponent extends ExpandedItemRendererComponentWrapper implements Accessible {
    private Accessible myAccessible;
    MyAccessibleComponent(@NotNull Component comp, @NotNull Accessible accessible) {
      super(comp);
      myAccessible = accessible;
    }
    @Override
    public AccessibleContext getAccessibleContext() {
      return accessibleContext = myAccessible.getAccessibleContext();
    }
  }

  @Override
  public void setBorder(Border border) {
    JComponent rendererComponent = getRendererComponent();
    if (rendererComponent != null) {
      rendererComponent.setBorder(border);
      return;
    }
    super.setBorder(border);
  }

  @Override
  public String getToolTipText() {
    JComponent rendererComponent = getRendererComponent();
    if (rendererComponent != null) {
      return rendererComponent.getToolTipText();
    }
    return super.getToolTipText();
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    JComponent rendererComponent = getRendererComponent();
    if (rendererComponent != null) {
      return rendererComponent.getToolTipText(event);
    }
    return super.getToolTipText(event);
  }

  @Override
  public Point getToolTipLocation(MouseEvent event) {
    JComponent rendererComponent = getRendererComponent();
    if (rendererComponent != null) {
      return rendererComponent.getToolTipLocation(event);
    }
    return super.getToolTipLocation(event);
  }

  @Nullable
  private JComponent getRendererComponent() {
    if (getComponentCount() == 1) {
      Component component = getComponent(0);
      if (component instanceof JComponent) {
        return ((JComponent)component);
      }
    }
    return null;
  }
}
