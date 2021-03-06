// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ui.hover.HoverListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;

import static javax.swing.SwingUtilities.convertPointFromScreen;

final class HoverService {
  private final ComponentPoint outer = new ComponentPoint();
  private final ComponentPoint inner = new ComponentPoint();

  void process(@NotNull AWTEvent event) {
    if (event instanceof MouseEvent) {
      process((MouseEvent)event);
    }
  }

  private void process(@NotNull MouseEvent event) {
    Component component = getShowingComponent(event);
    outer.updateComponent(component);
    outer.x = event.getXOnScreen();
    outer.y = event.getYOnScreen();
    updateHovered(component == null ? null : getHoveredComponent(component, event.getX(), event.getY()));
  }

  private void updateHovered(@Nullable Component component) {
    Component old = inner.updateComponent(component);
    if (component != old && old != null) {
      HoverListener.getAll(old).forEach(listener -> listener.mouseExited(old));
    }
    if (component != null) {
      Point point = new Point(outer.x, outer.y);
      convertPointFromScreen(point, component);
      if (component != old) {
        inner.x = point.x;
        inner.y = point.y;
        HoverListener.getAll(component).forEach(listener -> listener.mouseEntered(component, point.x, point.y));
      }
      else if (inner.x != point.x || inner.y != point.y) {
        inner.x = point.x;
        inner.y = point.y;
        HoverListener.getAll(component).forEach(listener -> listener.mouseMoved(component, point.x, point.y));
      }
    }
  }

  private static @Nullable Component getShowingComponent(@NotNull MouseEvent event) {
    if (MouseEvent.MOUSE_EXITED == event.getID()) return null;
    Component component = event.getComponent();
    return component != null && component.isShowing() ? component : null;
  }

  private static @Nullable Component getHoveredComponent(@NotNull Component parent, @Nullable Component child, int x, int y) {
    return parent != child && child != null && child.isVisible()
           ? getHoveredComponent(child, x - child.getX(), y - child.getY())
           : null;
  }

  private static @Nullable Component getHoveredComponent(@NotNull Component parent, int x, int y) {
    if (parent instanceof Container) {
      if (!parent.contains(x, y)) return null;
      Container container = (Container)parent;
      for (Component child : container.getComponents()) {
        Component component = getHoveredComponent(parent, child, x, y);
        if (component != null) return component;
      }
    }
    else {
      Component component = getHoveredComponent(parent, parent.getComponentAt(x, y), x, y);
      if (component != null) return component;
    }
    return HoverListener.getAll(parent).isEmpty() ? null : parent;
  }


  private static final class ComponentPoint {
    private WeakReference<Component> reference;
    int x;
    int y;

    @Nullable
    Component getComponent() {
      WeakReference<Component> reference = this.reference;
      return reference == null ? null : reference.get();
    }

    @Nullable
    Component updateComponent(@Nullable Component component) {
      Component old = getComponent();
      if (component == null) {
        reference = null;
      }
      else if (component != old) {
        reference = new WeakReference<>(component);
      }
      return old;
    }
  }
}
