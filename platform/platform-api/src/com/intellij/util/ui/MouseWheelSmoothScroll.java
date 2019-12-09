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
import java.util.Iterator;
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
        return v - bar.getValue() != 0 || minimum != bar.getMinimum() || maximum != bar.getMaximum() || !bar.isShowing();
      });
      e.consume();
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

    private final static int REFRESH_TIME = 1000 / 60; // 60 Hz
    private final static double VELOCITY_THRESHOLD = 0.001;
    private double myVelocity = Double.NaN;
    private double myLambda = Double.NaN;
    private double myCurrentValue = Double.NaN, myTargetValue = Double.NaN;
    private long myLastEventTime = -1;

    private final Consumer<Integer> BLACK_HOLE = (x) -> {};
    private @NotNull Consumer<Integer> myConsumer = BLACK_HOLE;
    private final Predicate<Integer> FALSE_PREDICATE = (value) -> false;
    private @NotNull Predicate<Integer> myShouldStop = FALSE_PREDICATE;

    private final Timer myTimer = TimerUtil.createNamedTimer("Inertial Animation Timer", REFRESH_TIME, this);
    private final EventCounter myTouchpadRecognizer = new EventCounter(100);

    private InertialAnimator() {
      myTimer.setInitialDelay(0);
    }

    public double getDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.duration")), 0);
    }

    public double getDecayDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.decay.duration")), 0);
    }

    public int getTouchpadThreshold() {
      return Registry.intValue("idea.inertial.smooth.scrolling.touchpad.threshold");
    }

    public final void start(int initValue, int targetValue, @NotNull Consumer<Integer> consumer, @Nullable Predicate<Integer> shouldStop) {
      long currentEventTime = System.currentTimeMillis();
      myTouchpadRecognizer.addTime(currentEventTime);
      double duration = getDuration();
      if (duration == 0 || myTouchpadRecognizer.getSize() >= getTouchpadThreshold()) {
        consumer.accept(targetValue);
        stop();
        return;
      }

      boolean isSameDirection = myVelocity * (targetValue - initValue) > 0;
      if (isSameDirection) {
        myTargetValue = (targetValue - initValue) + myTargetValue;
      } else {
        myTargetValue = targetValue;
      }

      myLastEventTime = currentEventTime - myTimer.getDelay();
      myVelocity = (myTargetValue - initValue) / duration;
      myLambda = 1.0;
      myConsumer = Objects.requireNonNull(consumer);
      myShouldStop = shouldStop == null ? FALSE_PREDICATE : shouldStop;
      myCurrentValue = initValue;
      myTimer.start();
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
      if (myShouldStop.test((int)round(myCurrentValue))) {
        stop();
        return;
      }

      long eventTime = System.currentTimeMillis();
      myCurrentValue += myVelocity * (eventTime - myLastEventTime);
      myVelocity *= myLambda;
      myLastEventTime = eventTime;

      int nextValue = (int)round(myCurrentValue);
      myConsumer.accept(nextValue);

      // slowdown the animation
      double animationTimeLeft = (myTargetValue - myCurrentValue) / myVelocity;
      if (myLambda == 1.0 && animationTimeLeft > REFRESH_TIME && animationTimeLeft < getDecayDuration()) {
        // find q of geometric progression using n-th member formulae
        myLambda = pow(abs(VELOCITY_THRESHOLD / myVelocity), 1.0 / (animationTimeLeft / REFRESH_TIME));
      }

      if (abs(myVelocity) < VELOCITY_THRESHOLD || (myVelocity > 0 ? nextValue > myTargetValue : nextValue < myTargetValue)) {
        stop();
      }
    }

    public final void stop() {
      boolean isAlreadyStopped = myLastEventTime < 0;
      if (isAlreadyStopped) {
        return;
      }
      myTimer.stop();
      myVelocity = Double.NaN;
      myLambda = Double.NaN;
      myCurrentValue = Double.NaN;
      myTargetValue = Double.NaN;
      myLastEventTime = -1;
      myConsumer = BLACK_HOLE;
      myShouldStop = FALSE_PREDICATE;
    }
  }

  private final static class EventCounter {
    private final List<Long> myValues = new LinkedList<>();
    private final long myDuration;


    private EventCounter(long duration) {
      myDuration = max(duration, 1);
    }

    public void addTime(long value) {
      myValues.add(value);
      Iterator<Long> it = myValues.iterator();
      while (it.hasNext() && it.next() <= value - myDuration) {
        it.remove();
      }
    }

    public int getSize() {
      return myValues.size();
    }
  }

}
