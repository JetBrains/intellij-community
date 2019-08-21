// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.Math.*;

@ApiStatus.Experimental
public class MouseWheelSmoothScroll {

  private final InertialAnimator horizontal = new InertialAnimator(), vertical = new InertialAnimator();
  private final @NotNull Supplier<Boolean> myScrollEnabled;

  public static MouseWheelSmoothScroll create() {
    return create(() -> true);
  }

  public static MouseWheelSmoothScroll create(@NotNull Supplier<Boolean> isScrollEnabled) {
    return new MouseWheelSmoothScroll(isScrollEnabled);
  }

  private MouseWheelSmoothScroll(@NotNull Supplier<Boolean> isEnabledChecker) {
    myScrollEnabled = Objects.requireNonNull(isEnabledChecker);
  }

  /**
   * Handles mouse wheel event and adds animation with inertia.
   * @param e any mouse wheel event
   * @param alternative handle event alternative way, when cannot add animation.
   */
  public void processMouseWheelEvent(@NotNull MouseWheelEvent e, @Nullable Consumer<MouseWheelEvent> alternative) {
    JScrollBar bar = !myScrollEnabled.get() ? null : getEventScrollBar(e);
    if (bar == null) {
      if (alternative != null) alternative.accept(e);
      return;
    }

    InertialAnimator animator = isHorizontalScroll(e) ? horizontal : vertical;
    int delta = (int)round(getDelta(bar, e));
    int value = bar.getValue();
    int minimum = bar.getMinimum();
    int maximum = bar.getMaximum();
    if (abs(delta) > 0.01) { // ignore small delta event
      animator.start(value, value + delta, bar::setValue, (v) -> {
        return v - bar.getValue() != 0 || minimum != bar.getMinimum() || maximum != bar.getMaximum();
      });
    }
  }

  public static @Nullable JScrollBar getEventScrollBar(@NotNull MouseWheelEvent e) {
    JScrollPane scroller = (JScrollPane) e.getComponent();
    if (scroller == null) return null;
    return isHorizontalScroll(e) ? scroller.getHorizontalScrollBar() : scroller.getVerticalScrollBar();
  }

  public static boolean isHorizontalScroll(@NotNull MouseWheelEvent e) {
    return e.isShiftDown();
  }

  public static int getUnitIncrement() {
    return Registry.intValue("idea.inertial.smooth.scrolling.unit.increment");
  }

  public static double getDelta(@NotNull JScrollBar bar, @NotNull MouseWheelEvent event) {
    double rotation = event.getPreciseWheelRotation();
    int direction = rotation < 0 ? -1 : 1;
    // bar.getUnitIncrement can return -1 for top bound value. Fix it
    int increment = getUnitIncrement();
    int unitIncrement = max(increment < 0 ? bar.getUnitIncrement(direction) : increment, 0);
    return unitIncrement * rotation * event.getScrollAmount();
  }

  private final static class InertialAnimator implements ActionListener {

    private static final int HI_POLLING_TIME = 10; // high intensive polling time for events

    private final int REFRESH_TIME = 1000 / getRefreshRate();

    private double myVelocity = 0.0;
    private double myCurrentValue = 0.0;

    private final Consumer<Integer> BLACK_HOLE = (x) -> {};
    private @NotNull Consumer<Integer> myConsumer = BLACK_HOLE;
    private final Predicate<Integer> FALSE_PREDICATE = (value) -> false;
    private @NotNull Predicate<Integer> myShouldStop = FALSE_PREDICATE;

    private final Timer myTimer = new Timer(REFRESH_TIME, this);
    private final AverageDiff<Long> myAvgDiff = new AverageDiff<>(8);

    public double getTouchpadVelocityFactor() {
      return Registry.doubleValue("idea.inertial.smooth.scrolling.touchpad.velocity.factor");
    }

    public double getVelocityFactor() {
      return Registry.doubleValue("idea.inertial.smooth.scrolling.velocity.factor");
    }
    public double getVelocityDecayFactor() {
      double value = Registry.doubleValue("idea.inertial.smooth.scrolling.velocity.decay");
      return -max(abs(value), 0.001);
    }

    public int getDuration() {
      return Registry.intValue("idea.inertial.smooth.scrolling.duration");
    }

    public int getRefreshRate() {
      return 60;
    }

    public final void start(int initValue, int targetValue, @NotNull Consumer<Integer> consumer, @Nullable Predicate<Integer> shouldStop) {
      if (getDuration() == 0) {
        stop();
        consumer.accept(targetValue);
        return;
      }

      myAvgDiff.add(System.currentTimeMillis());
      double multiplierFactor = myAvgDiff.getAverage() <= HI_POLLING_TIME ? getTouchpadVelocityFactor() : getVelocityFactor();
      myVelocity = multiplierFactor * (targetValue - initValue) / getDuration();
      myConsumer = Objects.requireNonNull(consumer);
      myShouldStop = shouldStop == null ? FALSE_PREDICATE : shouldStop;
      myCurrentValue = initValue;
      myTimer.start();
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
      if (abs(myVelocity) < 0.001 || myShouldStop.test((int)round(myCurrentValue))) {
        stop();
        return;
      }

      myCurrentValue += myVelocity * REFRESH_TIME;
      int nextValue = (int)round(myCurrentValue);
      myConsumer.accept(nextValue);
      myVelocity *= exp(getVelocityDecayFactor() * REFRESH_TIME);
    }

    public final void stop() {
      myTimer.stop();
      myConsumer = BLACK_HOLE;
      myShouldStop = FALSE_PREDICATE;
      myVelocity = 0.0;
      myAvgDiff.clear();
    }
  }

  private final static class AverageDiff<T extends Number> {
    private final List<Double> myValues = new LinkedList<>();
    private final int myCapacity;
    private T myLastValue;


    private AverageDiff(int capacity) {
      myCapacity = max(capacity, 1);
    }

    public void add(@NotNull T value) {
      if (myLastValue != null) {
        myValues.add(value.doubleValue() - myLastValue.doubleValue());
        while (myValues.size() > myCapacity) {
          myValues.remove(0);
        }
      }
      myLastValue = value;
    }

    public void put(@NotNull T value) {
      myValues.add(value.doubleValue());
      while (myValues.size() > myCapacity) {
        myValues.remove(0);
      }
      myLastValue = value;
    }

    public double getAverage() {
      if (myValues.size() < myCapacity) return Double.NaN;
      return myValues.stream().reduce(0.0, Double::sum) / myValues.size();
    }

    public void clear() {
      myValues.clear();
      myLastValue = null;
    }
  }

}
