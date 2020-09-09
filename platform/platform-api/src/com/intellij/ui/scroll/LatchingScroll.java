// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scroll;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.LinkedList;
import java.util.List;

/**
 * The utility class that helps to avoid accidental scroll in perpendicular direction.
 */
@ApiStatus.Internal
public final class LatchingScroll {

  private final List<MyScrollEvent> myScrollEvents = new LinkedList<>();

  /**
   * Checks, if current event should be ignored.
   *
   * The class tracks all events which are passed into this method,
   * and calculates, if current one should be ignored. With some expire time
   * previous events are removed from tracker.
   */
  public boolean shouldBeIgnored(MouseWheelEvent event) {
    var source = (JScrollPane)event.getSource();
    // do not process any event from JBScrollPane with this option
    if (UIUtil.isClientPropertyTrue(getViewportView(source), JBScrollPane.IGNORE_SCROLL_LATCHING)) {
      return false;
    }

    myScrollEvents.add(new MyScrollEvent(event.getWhen(), event.getPreciseWheelRotation(), event.isShiftDown()));
    double xs = 0.0, ys = 0.0;

    var iterator = myScrollEvents.iterator();
    while (iterator.hasNext()) {
      MyScrollEvent se = iterator.next();
      if (se.getWhen() + getExpireAfter() < event.getWhen()) {
        iterator.remove();
      } else {
        if (se.isHorizontal()) {
          xs += Math.abs(se.getRotation());
        } else {
          ys += Math.abs(se.getRotation());
        }
      }
    }

    double angle = Math.toDegrees(Math.atan(Math.abs(ys / xs)));
    boolean isHorizontal = event.isShiftDown();

    double thatAngle = getAngle();
    if (angle <= thatAngle && !isHorizontal || angle >= 90 - thatAngle && isHorizontal) {
      return true;
    }

    return false;
  }

  private static @Nullable Component getViewportView(@NotNull JScrollPane pane) {
    JViewport viewport = pane.getViewport();
    if (viewport != null) {
      return viewport.getView();
    }
    return null;
  }

  private static long getExpireAfter() {
    return Math.max(Registry.intValue("idea.latching.scrolling.time"), 0);
  }

  private static double getAngle() {
    return Math.min(Math.max(Registry.doubleValue("idea.latching.scrolling.angle"), 0.0), 45.0);
  }

  public static boolean isEnabled() {
    return Registry.is("idea.latching.scrolling.enabled", false);
  }

  private static class MyScrollEvent {
    private final long myWhen;
    private final double myRotation;
    private final boolean isMyHorizontal;

    private MyScrollEvent(long when, double rotation, boolean isHorizontal) {
      myWhen = when;
      myRotation = rotation;
      isMyHorizontal = isHorizontal;
    }

    private long getWhen() {
      return myWhen;
    }

    private double getRotation() {
      return myRotation;
    }

    private boolean isHorizontal() {
      return isMyHorizontal;
    }
  }
}
