// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.hover.HoverListener;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class HoverService {
  private static final Logger LOG = Logger.getInstance(HoverService.class);
  private final List<ComponentPoint> hierarchy = new ArrayList<>();

  void process(@NotNull AWTEvent event) {
    if (event instanceof MouseEvent) {
      process((MouseEvent)event);
    }
  }

  private void process(@NotNull MouseEvent event) {
    SmartList<Component> components = new SmartList<>();
    if (MouseEvent.MOUSE_EXITED != event.getID()) {
      Component parent = event.getComponent();
      if (parent != null && parent.isShowing()) {
        Component component = getDeepestComponentAt(parent, event.getX(), event.getY());
        while (component != null && !(component instanceof Window)) {
          if (!HoverListener.getAll(component).isEmpty()) components.add(0, component);
          component = component.getParent();
        }
      }
    }
    int componentsCount = components.size();
    int hierarchySize = hierarchy.size();
    int index = 0;
    while (index < hierarchySize && index < componentsCount && components.get(index) == hierarchy.get(index).reference.get()) index++;
    while (index < hierarchySize) hierarchy.remove(--hierarchySize).mouseExited();
    if (index == componentsCount) {
      // notify only the deepest component in the hover hierarchy
      if (index > 0) hierarchy.get(index - 1).mouseMoved(event);
    }
    else {
      while (index < componentsCount) {
        ComponentPoint point = new ComponentPoint(components.get(index++));
        hierarchy.add(point);
        point.mouseEntered(event);
      }
    }
  }

  /**
   * @param parent the component to begin the search
   * @param x      the x target location
   * @param y      the y target location
   * @return the deepest visible descendent component of {@code parent}
   * that contains the location {@code x}, {@code y}; or {@code null},
   * if it does not contain the specified location
   * @see SwingUtilities#getDeepestComponentAt
   */
  private static Component getDeepestComponentAt(@NotNull Component parent, int x, int y) {
    if (!parent.contains(x, y)) return null; // parent does not contain the specified location
    if (parent instanceof Container) {
      for (Component child : ((Container)parent).getComponents()) {
        if (child != null && child.isVisible()) {
          Component deepest = child instanceof Container
                              ? getDeepestComponentAt(child, x - child.getX(), y - child.getY())
                              : child.getComponentAt(x - child.getX(), y - child.getY());
          if (deepest != null && deepest.isVisible()) return deepest; // the deepest component that contains the specified location
        }
      }
    }
    // do not allow returning visible glass pane without components
    return parent instanceof IdeGlassPane ? null : parent;
  }


  private static final class ComponentPoint {
    private final WeakReference<Component> reference;
    private final Point point = new Point();

    private ComponentPoint(@NotNull Component component) {
      reference = new WeakReference<>(component);
    }

    private void mouseEntered(@NotNull MouseEvent event) {
      Component component = reference.get();
      if (component == null) return; // component is already collected
      point.setLocation(event.getXOnScreen(), event.getYOnScreen());
      SwingUtilities.convertPointFromScreen(point, component);
      if (LOG.isDebugEnabled()) LOG.debug("mouse entered into " + component);
      notifySafely(component, listener -> listener.mouseEntered(component, point.x, point.y));
    }

    private void mouseMoved(@NotNull MouseEvent event) {
      Component component = reference.get();
      if (component == null) return; // component is already collected
      int x = point.x;
      int y = point.y;
      point.setLocation(event.getXOnScreen(), event.getYOnScreen());
      SwingUtilities.convertPointFromScreen(point, component);
      if (point.x == x && point.y == y) return; // mouse location is not changed
      if (LOG.isTraceEnabled()) LOG.trace("mouse moved in " + component);
      notifySafely(component, listener -> listener.mouseMoved(component, point.x, point.y));
    }

    private void mouseExited() {
      Component component = reference.get();
      if (component == null) return; // component is already collected
      if (LOG.isDebugEnabled()) LOG.debug("mouse exited from " + component);
      notifySafely(component, listener -> listener.mouseExited(component));
    }

    private static void notifySafely(@NotNull Component component, @NotNull Consumer<HoverListener> notify) {
      for (HoverListener listener : HoverListener.getAll(component)) {
        try {
          notify.accept(listener);
        }
        catch (Exception exception) {
          LOG.error(exception);
        }
      }
    }
  }
}
