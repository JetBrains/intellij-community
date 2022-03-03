// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scroll;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.ui.scroll.SmoothScrollUtil.getEventScrollBar;
import static com.intellij.ui.scroll.SmoothScrollUtil.isHorizontalScroll;
import static java.lang.Math.*;

@ApiStatus.Internal
public final class MouseWheelSmoothScroll {

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
  public void processMouseWheelEvent(@NotNull MouseWheelEvent e, @Nullable Consumer<? super MouseWheelEvent> alternative) {
    JScrollBar bar = !myScrollEnabled.get() ? null : getEventScrollBar(e);
    if (bar == null) {
      if (alternative != null) alternative.accept(e);
      return;
    }

    InertialAnimator animator = isHorizontalScroll(e) ? horizontal : vertical;
    int value = bar.getValue();
    int delta = (int)getDelta(bar, e, animator.myTargetValue);
    if (delta == 0) {
      return;
    }

    animator.start(value, value + delta, bar::setValue, shouldStop(bar), ScrollAnimationSettings.SETTINGS);

    e.consume();
  }

  private static Predicate<Integer> shouldStop(JScrollBar bar) {
    return (v) -> {
      return v - bar.getValue() != 0 || !bar.isShowing();
    };
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

  static class InertialAnimator implements ActionListener {

    private final static int REFRESH_TIME = 1000 / 250;
    private double myInitValue = Double.NaN, myCurrentValue = Double.NaN, myTargetValue = Double.NaN;
    private long myStartEventTime = -1, myLastEventTime = -1, myDuration = -1;
    private AnimationSettings mySettings;

    private final Consumer<? super Integer> BLACK_HOLE = (x) -> {};
    private @NotNull Consumer<? super Integer> myConsumer = BLACK_HOLE;
    private final Predicate<? super Integer> FALSE_PREDICATE = (value) -> false;
    private @NotNull Predicate<? super Integer> myShouldStop = FALSE_PREDICATE;

    private final Timer myTimer = TimerUtil.createNamedTimer("Inertial Animation Timer", REFRESH_TIME, this);

    InertialAnimator() {
      myTimer.setInitialDelay(0);
    }

    public final void start(int initValue, int targetValue,
                            @NotNull Consumer<? super Integer> consumer,
                            @Nullable Predicate<? super Integer> shouldStop,
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

  interface AnimationSettings {
    double getDuration();
    @NotNull Easing getEasing();
  }

  enum ScrollAnimationSettings implements AnimationSettings {
    SETTINGS {
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
    }
  }

  interface Easing {
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

  static class CubicBezierEasing implements Easing {

    private final com.intellij.util.animation.CubicBezierEasing delegate;

    CubicBezierEasing(double c1x, double c1y, double c2x, double c2y, int size) {
      delegate = new com.intellij.util.animation.CubicBezierEasing(c1x, c1y, c2x, c2y, size);
    }

    public void update(double c1x, double c1y, double c2x, double c2y) {
      delegate.update(c1x, c1y, c2x, c2y);
    }

    public int getSize() {
      return delegate.getSize();
    }

    @Override
    public double calc(double t, double b, double c, double d) {
      double x = t / d;
      return c * delegate.calc(x) + b;
    }
  }

}
