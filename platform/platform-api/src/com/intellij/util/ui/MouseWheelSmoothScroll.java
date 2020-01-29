// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.Math.*;

@ApiStatus.Experimental
public class MouseWheelSmoothScroll {

  private final InertialAnimator horizontal = new InertialAnimator(), vertical = new InertialAnimator();
  private final FlingManager horizontalFling = new FlingManager(), verticalFling = new FlingManager();
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
    int value = bar.getValue();
    int delta;
    if (TouchScrollUtil.isTouchScroll(e)) {
      delta = getRoundedAtLeastToOne(TouchScrollUtil.getDelta(e));
    } else {
      delta = (int)getDelta(bar, e, animator.myTargetValue);
      if (delta == 0) {
        return;
      }
    }
    int targetValue = value + delta;

    if (TouchScrollUtil.isBegin(e)) {
      verticalFling.registerBegin();
      horizontalFling.registerBegin();
    } else if (TouchScrollUtil.isUpdate(e)) {
      bar.setValue(targetValue);
      FlingManager fling = isHorizontalScroll(e) ? horizontalFling : verticalFling;
      fling.registerUpdate(delta);
    } else if (TouchScrollUtil.isEnd(e)) {
      verticalFling.start(getEventVerticalScrollBar(e), vertical);
      horizontalFling.start(getEventHorizontalScrollBar(e), horizontal);
    } else {
      animator.start(value, targetValue, bar::setValue, shouldStop(bar), DefaultAnimationSettings.SCROLL);
    }

    e.consume();
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
    return (v) -> {
      return v - bar.getValue() != 0 || !bar.isShowing();
    };
  }

  public static boolean isHorizontalScroll(@NotNull MouseWheelEvent e) {
    return e.isShiftDown();
  }

  private static double getDelta(@NotNull JScrollBar bar, @NotNull MouseWheelEvent event, double animationTargetValue) {
    double rotation = event.getPreciseWheelRotation();
    int direction = rotation < 0 ? -1 : 1;

    if (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
      return direction * bar.getBlockIncrement(direction);
    }

    if (event.getSource() instanceof JScrollPane) {
      JViewport viewport = ((JScrollPane)event.getSource()).getViewport();
      if (viewport.getView() instanceof Scrollable) {
        int orientation = bar.getOrientation();
        boolean isVertical = orientation == Adjustable.VERTICAL;
        Scrollable scrollable = (Scrollable)viewport.getView();
        int scroll = abs(event.getUnitsToScroll());
        Rectangle rect = viewport.getViewRect();
        int delta = 0;
        if (!Double.isNaN(animationTargetValue)) {
          if (isVertical) {
            rect.y = (int)animationTargetValue;
          }
          else {
            rect.x = (int)animationTargetValue;
          }
        }
        for (int i = 0; i < scroll; i++) {
          int increment = max(scrollable.getScrollableUnitIncrement(rect, orientation, direction), 0) * direction;
          if (isVertical) {
            rect.y += increment;
          }
          else {
            rect.x += increment;
          }
          delta += increment;
        }
        return delta;
      }
    }

    int increment = bar.getUnitIncrement(direction);
    int delta = increment * event.getUnitsToScroll();
    return delta == 0 ? rotation : delta;
  }

  private static int getRoundedAtLeastToOne(double value) {
    if (abs(value) < 1) {
      return value > 0 ? 1 : -1;
    }
    else {
      return (int) round(value);
    }
  }

  private static class InertialAnimator implements ActionListener {

    private final static int REFRESH_TIME = 1000 / 144;
    private double myInitValue = Double.NaN, myCurrentValue = Double.NaN, myTargetValue = Double.NaN;
    private long myStartEventTime = -1, myLastEventTime = -1, myDuration = -1;
    private AnimationSettings mySettings;

    private final Consumer<Integer> BLACK_HOLE = (x) -> {};
    private @NotNull Consumer<Integer> myConsumer = BLACK_HOLE;
    private final Predicate<Integer> FALSE_PREDICATE = (value) -> false;
    private @NotNull Predicate<Integer> myShouldStop = FALSE_PREDICATE;

    private final Timer myTimer = TimerUtil.createNamedTimer("Inertial Animation Timer", REFRESH_TIME, this);

    private InertialAnimator() {
      myTimer.setInitialDelay(0);
    }

    public final void start(int initValue, int targetValue,
                            @NotNull Consumer<Integer> consumer,
                            @Nullable Predicate<Integer> shouldStop,
                            @NotNull AnimationSettings settings) {
      mySettings = settings;
      double duration = mySettings.getDuration();
      if (duration == 0) {
        consumer.accept(targetValue);
        stop();
        return;
      }

      boolean isSameDirection = (myTargetValue - myInitValue) * (targetValue - initValue) > 0;
      if (isSameDirection && myTimer.isRunning()) {
        myTargetValue += (targetValue - initValue);
        myDuration = (long)duration + max(myLastEventTime - myStartEventTime, 0);
        myInitValue = myCurrentValue;
        myStartEventTime = myLastEventTime;
      } else {
        myTargetValue = targetValue;
        myDuration = (long)duration;
        myInitValue = initValue;
        myStartEventTime = System.currentTimeMillis();
      }

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

      myLastEventTime = System.currentTimeMillis();
      long currentEventTime = min(myLastEventTime, myStartEventTime + myDuration);

      myCurrentValue = mySettings.getEasing().calc(currentEventTime - myStartEventTime,
                                                   myInitValue,
                                                   myTargetValue - myInitValue,
                                                   myDuration);
      myConsumer.accept((int) round(myCurrentValue));

      if (myLastEventTime >= myStartEventTime + myDuration) {
        stop();
      }
    }

    public final void stop() {
      myTimer.stop();
      myDuration = myLastEventTime = myStartEventTime = -1;
      myInitValue = myCurrentValue = myTargetValue = Double.NaN;
      myConsumer = BLACK_HOLE;
      myShouldStop = FALSE_PREDICATE;
      mySettings = null;
    }
  }

  private static class FlingManager {
    private long beginTime = 0L;
    private int lastDelta = 0;

    public void registerBegin() {
      beginTime = System.currentTimeMillis();
    }

    public void registerUpdate(int delta) {
      beginTime = 0L;
      lastDelta = delta;
    }

    private static int getPixelThreshold() {
      return Registry.intValue("idea.inertial.touch.fling.pixelThreshold");
    }

    private static int getFlingMultiplier() {
      return Registry.intValue("idea.inertial.touch.fling.multiplier");
    }

    private static int getFlingInterruptLag() {
      return Registry.intValue("idea.inertial.touch.fling.interruptLag");
    }

    public boolean shouldStart() {
      return abs(lastDelta) >= getPixelThreshold();
    }

    private Predicate<Integer> shouldStop(JScrollBar bar) {
      return MouseWheelSmoothScroll.shouldStop(bar).or(v -> {
        return beginTime != 0L && (System.currentTimeMillis() - beginTime) > getFlingInterruptLag();
      });
    }

    public void start(JScrollBar bar, InertialAnimator animator) {
      if (bar != null && shouldStart()) {
        int initValue = bar.getValue();
        int targetValue = initValue + lastDelta * getFlingMultiplier();
        animator.start(initValue, targetValue, bar::setValue, shouldStop(bar), DefaultAnimationSettings.TOUCH);
      }
    }
  }

  private interface AnimationSettings {
    double getDuration();
    @NotNull Easing getEasing();
  }

  private enum DefaultAnimationSettings implements AnimationSettings {

    SCROLL {

      private CubicBezierEasing ourEasing;
      private int curvePoints;


      @Override
      public double getDuration() {
        return UISettings.getShadowInstance().getAnimatedScrollingDuration();
      }

      @NotNull
      @Override
      public Easing getEasing() {
        int points = UISettings.getShadowInstance().getAnimatedScrollingCurvePoints();
        if (points != curvePoints || ourEasing == null) {
          double x1 = (points >> 24 & 0xFF) / 200.0;
          double y1 = (points >> 16 & 0xFF) / 200.0;
          double x2 = (points >> 8 & 0xFF) / 200.0;
          double y2 = (points & 0xFF) / 200.0;
          if (ourEasing == null) {
            ourEasing = new CubicBezierEasing(x1, y1, x2, y2, 2000);
          } else {
            ourEasing.update(x1, y1, x2, y2);
          }
          curvePoints = points;
        }
        return ourEasing;
      }
    },

    TOUCH {

      private Easing cubicEaseOut;

      @Override
      public double getDuration() {
        return max(abs(Registry.doubleValue("idea.inertial.smooth.scrolling.touch.duration")), 0);
      }

      @NotNull
      @Override
      public Easing getEasing() {
        if (cubicEaseOut == null) {
          cubicEaseOut = new CubicBezierEasing(0.215, 0.61, 0.355, 1, 2000);
        }
        return cubicEaseOut;
      }
    }

  }

  private interface Easing {
    /**
     * Calculates current point value.
     * @param t current time of animation
     * @param b start value
     * @param c total points count
     * @param d animation duration
     * @return calculated value
     */
    double calc(double t, double b, double c, double d);
  }

  private static class CubicBezierEasing implements Easing {

    private final double[] xs;
    private final double[] ys;

    private CubicBezierEasing(double c1x, double c1y, double c2x, double c2y, int size) {
      xs = new double[size];
      ys = new double[size];
      update(c1x, c1y, c2x, c2y);
    }

    public void update(double c1x, double c1y, double c2x, double c2y) {
      for (int i = 0; i < xs.length; i++) {
        xs[i] = bezier(i * 1. / xs.length, c1x, c2x);
        ys[i] = bezier(i * 1. / xs.length, c1y, c2y);
      }
    }

    public int getSize() {
      assert xs.length == ys.length;
      return xs.length;
    }

    @Override
    public double calc(double t, double b, double c, double d) {
      double x = t / d;
      int res = Arrays.binarySearch(xs, x);
      if (res < 0) {
        res = -res - 1;
      }
      return c * ys[min(res, ys.length - 1)] + b;
    }

    private static double bezier(double t, double u1, double u2) {
      double v = 1 - t;
      return 3 * u1 * v * v * t + 3 * u2 * v * t * t + t * t * t;
    }
  }

}
