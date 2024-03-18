// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.accessibility.AbstractAccessibleContextDelegate;
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

  private ExpandedItemRendererComponentWrapper(final @NotNull Component rendererComponent) {
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

  public static @NotNull ExpandedItemRendererComponentWrapper wrap(@NotNull Component rendererComponent) {
    if (rendererComponent instanceof Accessible) {
      return new MyComponent(rendererComponent, (Accessible)rendererComponent);
    }
    return new ExpandedItemRendererComponentWrapper(rendererComponent);
  }

  public static @Nullable Component unwrap(@NotNull Component rendererComponent) {
    if (rendererComponent instanceof ExpandedItemRendererComponentWrapper) {
      return ((ExpandedItemRendererComponentWrapper)rendererComponent).getDelegate();
    }
    return rendererComponent;
  }

  private static class MyComponent extends ExpandedItemRendererComponentWrapper implements Accessible {
    private final Accessible myAccessible;
    private AccessibleContext myDefaultAccessibleContext;

    MyComponent(@NotNull Component comp, @NotNull Accessible accessible) {
      super(comp);
      myAccessible = accessible;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleMyComponent();
      }
      return accessibleContext;
    }

    public AccessibleContext getDefaultAccessibleContext() {
      if (myDefaultAccessibleContext == null) {
        myDefaultAccessibleContext = new AccessibleJComponent() {};
      }
      return myDefaultAccessibleContext;
    }

    /**
     * Wraps the accessible context of {@link #myAccessible}, except for parent, which
     * needs to come from the default implementation to avoid infinite parent/child cycle.
     */
    protected class AccessibleMyComponent extends AbstractAccessibleContextDelegate {
      @Override
      protected @NotNull AccessibleContext getDelegate() {
        return myAccessible.getAccessibleContext();
      }

      @Override
      public Accessible getAccessibleParent() {
        return getDefaultAccessibleContext().getAccessibleParent();
      }

      @Override
      public int getAccessibleIndexInParent() {
        return getDefaultAccessibleContext().getAccessibleIndexInParent();
      }
    }
  }

  @Override
  public void setBorder(Border border) {
    Component c = getDelegate();
    if (c instanceof JComponent) {
      ((JComponent)c).setBorder(border);
      return;
    }
    super.setBorder(border);
  }

  @Override
  public String getToolTipText() {
    Component c = getDelegate();
    if (c instanceof JComponent) {
      return ((JComponent)c).getToolTipText();
    }
    return super.getToolTipText();
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    Component c = getDelegate();
    if (c instanceof JComponent) {
      return ((JComponent)c).getToolTipText(event);
    }
    return super.getToolTipText(event);
  }

  @Override
  public Point getToolTipLocation(MouseEvent event) {
    Component c = getDelegate();
    if (c instanceof JComponent) {
      return ((JComponent)c).getToolTipLocation(event);
    }
    return super.getToolTipLocation(event);
  }

  public @Nullable Component getDelegate() {
    return getComponentCount() == 1 ? getComponent(0) : null;
  }
}
