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
  private final FlingDetector horizontalFling = new FlingDetector(), verticalFling = new FlingDetector();
  private final EventCounter touchpadRecognizer = new EventCounter(100);
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

    int value = bar.getValue();
    int delta = (int)round(getDelta(bar, e));
    int targetValue = value + delta;

    if (TouchScrollUtil.isUpdate(e)) {
      bar.setValue(targetValue);
      FlingDetector fling = isHorizontalScroll(e) ? horizontalFling : verticalFling;
      fling.updateDelta(delta);
    } else if (TouchScrollUtil.isEnd(e)) {
      startFling(getEventVerticalScrollBar(e), verticalFling, vertical);
      startFling(getEventHorizontalScrollBar(e), horizontalFling, horizontal);
    } else if (abs(delta) != 0) { // ignore small delta event
      InertialAnimator animator = isHorizontalScroll(e) ? horizontal : vertical;
      touchpadRecognizer.addTime(System.currentTimeMillis());
      if (touchpadRecognizer.getSize() >= getTouchpadThreshold()) {
        bar.setValue(targetValue);
        animator.stop();
      } else {
        animator.start(value, targetValue, new ScrollAnimationSettings(), bar::setValue, shouldStop(bar));
      }
    }

    e.consume();
  }

  public void startFling(JScrollBar bar, FlingDetector fling, InertialAnimator animator) {
    if (bar != null && fling.shouldStart()) {
      animator.stop();
      int value = bar.getValue();
      int targetValue = fling.getTargetValue(value);
      animator.start(value, targetValue, new FlingAnimationSettings(), bar::setValue, shouldStop(bar));
    }
  }

  public static @Nullable
  JScrollBar getEventScrollBar(@NotNull MouseWheelEvent e) {
    return isHorizontalScroll(e) ? getEventHorizontalScrollBar(e) : getEventVerticalScrollBar(e);
  }

  public static @Nullable
  JScrollBar getEventHorizontalScrollBar(@NotNull MouseWheelEvent e) {
    JScrollPane scroller = (JScrollPane)e.getComponent();
    return scroller == null ? null : scroller.getHorizontalScrollBar();
  }

  public static @Nullable
  JScrollBar getEventVerticalScrollBar(@NotNull MouseWheelEvent e) {
    JScrollPane scroller = (JScrollPane)e.getComponent();
    return scroller == null ? null : scroller.getVerticalScrollBar();
  }

  private static Predicate<Integer> shouldStop(JScrollBar bar) {
    int minimum = bar.getMinimum();
    int maximum = bar.getMaximum();
    return (v) -> {
      return v - bar.getValue() != 0 || minimum != bar.getMinimum() || maximum != bar.getMaximum() || !bar.isShowing();
    };
  }

  public static boolean isHorizontalScroll(@NotNull MouseWheelEvent e) {
    return e.isShiftDown();
  }

  public static int getUnitIncrement() {
    return Registry.intValue("idea.inertial.smooth.scrolling.unit.increment");
  }

  public int getTouchpadThreshold() {
    return Registry.intValue("idea.inertial.smooth.scrolling.touchpad.threshold");
  }

  public static double getDelta(@NotNull JScrollBar bar, @NotNull MouseWheelEvent event) {
    if (TouchScrollUtil.isTouchScroll(event)) {
      return TouchScrollUtil.getDelta(event);
    }
    double rotation = event.getPreciseWheelRotation();
    int direction = rotation < 0 ? -1 : 1;
    // bar.getUnitIncrement can return -1 for top bound value. Fix it
    int increment = getUnitIncrement();
    int unitIncrement = max(increment < 0 ? bar.getUnitIncrement(direction) : increment, 0);
    return unitIncrement * rotation * event.getScrollAmount();
  }

  private static class InertialAnimator implements ActionListener {

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
    private AnimationSettings myAnimationSettings = null;

    private final Timer myTimer = TimerUtil.createNamedTimer("Inertial Animation Timer", REFRESH_TIME, this);

    private InertialAnimator() {
      myTimer.setInitialDelay(0);
    }

    public final void start(int initValue, int targetValue,
                            @NotNull AnimationSettings animationSettings,
                            @NotNull Consumer<Integer> consumer,
                            @Nullable Predicate<Integer> shouldStop) {
      double duration = animationSettings.getDuration();
      if (duration == 0) {
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

      myLastEventTime = System.currentTimeMillis() - myTimer.getDelay();
      myVelocity = (myTargetValue - initValue) / duration;
      myLambda = 1.0;
      myConsumer = Objects.requireNonNull(consumer);
      myShouldStop = shouldStop == null ? FALSE_PREDICATE : shouldStop;
      myAnimationSettings = animationSettings;
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
      if (myLambda == 1.0 && animationTimeLeft > REFRESH_TIME && animationTimeLeft < myAnimationSettings.getDecayDuration()) {
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

  private static class FlingDetector {
    private int lastDelta = 0;

    public void updateDelta(int delta) {
      lastDelta = delta;
    }

    private static int getPixelThreshold() {
      return Registry.intValue("idea.inertial.smooth.scrolling.touch.pixel.threshold");
    }

    private static int getFlingMultiplier() {
      return Registry.intValue("idea.inertial.smooth.scrolling.touch.multiplier");
    }

    public boolean shouldStart() {
      return abs(lastDelta) >= getPixelThreshold();
    }

    public int getTargetValue(int initValue) {
      return initValue + lastDelta * getFlingMultiplier();
    }
  }

  private interface AnimationSettings {
    double getDuration();
    double getDecayDuration();
  }

  private static class ScrollAnimationSettings implements AnimationSettings {
    @Override
    public double getDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.duration")), 0);
    }

    @Override
    public double getDecayDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.decay.duration")), 0);
    }
  }

  private static class FlingAnimationSettings implements AnimationSettings {
    @Override
    public double getDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.touch.duration")), 0);
    }

    @Override
    public double getDecayDuration() {
      return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.touch.decay")), 0);
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
