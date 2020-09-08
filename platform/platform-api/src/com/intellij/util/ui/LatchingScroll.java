// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.registry.Registry;

import java.awt.event.MouseWheelEvent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class LatchingScroll {

  private final List<MyScrollEvent> myScrollEvents = new LinkedList<>();

  public boolean test(MouseWheelEvent event) {
    myScrollEvents.add(new MyScrollEvent(event.getWhen(), event.getPreciseWheelRotation(), event.isShiftDown()));
    double xs = 0.0, ys = 0.0;

    Iterator<MyScrollEvent> iterator = myScrollEvents.iterator();
    while (iterator.hasNext()) {
      MyScrollEvent se = iterator.next();
      if (se.getWhen() + getTime() < event.getWhen()) {
        iterator.remove();
      } else {
        double rotation = Math.abs(se.getRotation());
        if (se.isHorizontal()) {
          xs += rotation;
        } else {
          ys += rotation;
        }
      }
    }

    double angle = Math.toDegrees(Math.atan(Math.abs(ys / xs)));
    boolean isHorizontal = event.isShiftDown();

    double thatAngle = getAngle();
    if (angle <= thatAngle && !isHorizontal || angle >= 90 - thatAngle && isHorizontal) {
      return false;
    }

    return true;
  }

  private long getTime() {
    return Math.max(Registry.intValue("idea.latching.scrolling.time"), 0);
  }

  private double getAngle() {
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
