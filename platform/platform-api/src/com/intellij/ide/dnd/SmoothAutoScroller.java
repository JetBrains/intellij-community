// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.dnd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.intellij.openapi.util.SystemInfo.JAVA_VERSION;
import static com.intellij.ui.scale.JBUIScale.sysScale;
import static com.intellij.util.ui.TimerUtil.createNamedTimer;

/**
 * This class provides the application-wide scroller for drag targets.
 * Note that the target component must not use default auto-scrolling.
 *
 * @see Autoscroll
 * @see JComponent#setAutoscrolls
 * @see TransferHandler.SwingDropTarget
 */
public final class SmoothAutoScroller {
  /**
   * This key is intended to switch on a smooth scrolling during drag-n-drop operations.
   *
   * @see Autoscroll
   * @see JComponent#setAutoscrolls
   * @see JComponent#putClientProperty
   */
  @ApiStatus.Experimental
  public static final Key<Boolean> ENABLED = Key.create("SmoothAutoScroller enabled");

  /**
   * @return a listener to control shared auto-scroller
   */
  @ApiStatus.Experimental
  public static @NotNull DropTargetListener getSharedListener() {
    return ScrollListener.SHARED;
  }

  /**
   * @see SwingUtilities#installSwingDropTargetAsNecessary(Component, TransferHandler)
   */
  @ApiStatus.Experimental
  public static void installDropTargetAsNecessary(@NotNull JComponent component) {
    if (component instanceof Autoscroll) return; // Swing DnD
    component.putClientProperty(ENABLED, true);
    component.setAutoscrolls(false); // disable default scroller if needed
    DropTarget target = component.getDropTarget();
    if (target == null && !GraphicsEnvironment.isHeadless()) {
      component.setDropTarget(new DropTarget(component, DragListener.ACTION, DragListener.SHARED));
    }
  }

  @ApiStatus.Internal
  public static void recreateDragListener() {
    DragListener.SHARED.recreateListener();
  }

  /**
   * This is a replacement for TransferHandler.DropHandler
   * that replaces hardcoded auto-scrolling with the smooth auto-scrolling.
   * It depends on Swing's implementation and must be supported accordingly.
   */
  private static final class DragListener implements DropTargetListener {
    public static final int ACTION = DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK;
    private static final DragListener SHARED = new DragListener();
    private DropTargetListener listener;

    private DragListener() {
      createListener();
    }

    void recreateListener() {
      if (listener != null) { // Can be null if an exception was thrown during the creation.
        // TransferHandler cashes the listener in the data context, need to clear it first.
        AppContext.getAppContext().remove(listener.getClass());
      }
      createListener();
    }

    private void createListener() {
      try {
        listener = (DropTargetListener)MethodHandles.privateLookupIn(TransferHandler.class, MethodHandles.lookup())
          .findStatic(TransferHandler.class, "getDropTargetListener", MethodType.methodType(DropTargetListener.class))
          .invokeExact();
      }
      catch (Throwable e) {
        throw new InternalError("Unexpected JDK: " + JAVA_VERSION, e);
      }
    }

    @Override
    public void dragEnter(DropTargetDragEvent event) {
      getSharedListener().dragEnter(event);
      // ignore auto-scrolling from TransferHandler.DropHandler.dragEnter
      ReflectionUtil.setField(listener.getClass(), listener, Object.class, "state", null);
      ReflectionUtil.setField(listener.getClass(), listener, Component.class, "component", event.getDropTargetContext().getComponent());
      listener.dropActionChanged(event); // depends on implementation
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
      getSharedListener().dragOver(event);
      // ignore auto-scrolling from TransferHandler.DropHandler.dragOver
      listener.dropActionChanged(event); // depends on implementation
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
      getSharedListener().dropActionChanged(event);
      listener.dropActionChanged(event);
    }

    @Override
    public void dragExit(DropTargetEvent event) {
      getSharedListener().dragExit(event);
      listener.dragExit(event);
    }

    @Override
    public void drop(DropTargetDropEvent event) {
      getSharedListener().drop(event);
      listener.drop(event);
    }
  }


  private static final class ScrollListener implements DropTargetListener {
    private static final Logger LOG = Logger.getInstance(SmoothAutoScroller.class);
    private static final DropTargetListener SHARED = new ScrollListener();

    private DropTargetDragEvent event;
    private final Point screen = new Point();
    private final Timer timer = createNamedTimer("SmoothAutoScroller timer", 10, e -> {
      if (!validate(this.event)) update(null);
    });

    private ScrollListener() {
      LOG.debug("SmoothAutoScroller created");
    }

    private void update(DropTargetDragEvent event) {
      JComponent component = getComponent(event);
      if (component != null) {
        Point location = new Point(event.getLocation());
        SwingUtilities.convertPointToScreen(location, component);
        if (Registry.is("ide.dnd.to.front")) {
          Window window = ComponentUtil.getWindow(component);
          if (window != null) window.toFront();
        }
        this.screen.setLocation(location);
        this.event = event;
        if (!timer.isRunning()) {
          LOG.debug("SmoothAutoScroller started");
          timer.start();
        }
      }
      else {
        this.event = null;
        if (timer.isRunning()) {
          LOG.debug("SmoothAutoScroller stopped");
          timer.stop();
        }
      }
    }

    private boolean validate(DropTargetDragEvent event) {
      JComponent component = getComponent(event);
      if (component == null) {
        return false;
      }

      Point location = new Point(this.screen);
      SwingUtilities.convertPointFromScreen(location, component);

      Rectangle bounds = component.getVisibleRect();
      // mouse out of a component
      if (!bounds.contains(location.x, location.y)) {
        return false;
      }

      int margin = (int)(5 * sysScale(component));
      int deltaX = getDelta(3, margin, location.x, bounds.x, bounds.x + bounds.width);
      int deltaY = getDelta(5, margin, location.y, bounds.y, bounds.y + bounds.height);
      if (deltaX != 0 || deltaY != 0) {
        LOG.debug("SmoothAutoScroller delta X:", deltaX, " Y:", deltaY);
        bounds.x += deltaX;
        bounds.y += deltaY;
        SwingUtilities.convertPointToScreen(location, component);
        component.scrollRectToVisible(bounds);
        SwingUtilities.convertPointFromScreen(location, component);
      }
      DropTarget target = component.getDropTarget();
      if (target != null && !location.equals(event.getLocation())) {
        LOG.debug("SmoothAutoScroller simulates dragOver");
        target.dragOver(
          new DropTargetDragEvent(
            event.getDropTargetContext(),
            location,
            event.getDropAction(),
            event.getSourceActions())
        );
      }
      return true;
    }


    @Override
    public void dragEnter(DropTargetDragEvent event) {
      update(event); // start scrolling if possible
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
      update(event); // continue scrolling if possible
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
      update(event); // continue scrolling if possible
    }

    @Override
    public void dragExit(DropTargetEvent event) {
      update(null); // stop scrolling
    }

    @Override
    public void drop(DropTargetDropEvent event) {
      update(null); // stop scrolling
    }
  }

  private static JComponent getComponent(DropTargetDragEvent event) {
    if (event == null) return null; // no appropriate event
    Object source = event.getDropTargetContext().getComponent();
    JComponent component = source instanceof JComponent ? (JComponent)source : null;
    if (component == null) return null; // heavyweight components are not supported
    if (component instanceof Autoscroll) return null; // Swing DnD is used
    if (component.getAutoscrolls()) return null; // default scroller is used
    if (!component.isShowing()) return null; // the component is not visible on screen
    return ClientProperty.isTrue(component, ENABLED) ? component : null;
  }

  private static int getDelta(int count, int margin, int value, int min, int max) {
    int offset = Math.min(count * margin, (max - min) / 2);
    if (value < (min += offset)) {
      double delta = (min - value) / (double)margin;
      return count < delta ? 0 : -(int)Math.floor(delta * delta);
    }
    if (value > (max -= offset)) {
      double delta = (value - max) / (double)margin;
      return count < delta ? 0 : (int)Math.floor(delta * delta);
    }
    return 0;
  }
}
