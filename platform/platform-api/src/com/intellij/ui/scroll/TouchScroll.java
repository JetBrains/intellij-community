// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scroll;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.ui.scroll.MouseWheelSmoothScroll.*;
import static com.intellij.ui.scroll.SmoothScrollUtil.*;
import static java.lang.Math.abs;
import static java.lang.Math.max;

@ApiStatus.Internal
@ApiStatus.Experimental
public final class TouchScroll {
  private final InertialAnimator horizontal = new InertialAnimator(), vertical = new InertialAnimator();
  private final FlingManager horizontalFling = new FlingManager(), verticalFling = new FlingManager();
  private final @NotNull Supplier<Boolean> myScrollEnabled;

  public static TouchScroll create() {
    return create(() -> true);
  }

  public static TouchScroll create(@NotNull Supplier<Boolean> isScrollEnabled) {
    return new TouchScroll(isScrollEnabled);
  }

  private TouchScroll(@NotNull Supplier<Boolean> isEnabledChecker) {
    myScrollEnabled = Objects.requireNonNull(isEnabledChecker);
  }


  public void processMouseWheelEvent(@NotNull MouseWheelEvent e, @Nullable Consumer<? super MouseWheelEvent> alternative) {
    JScrollBar bar = !myScrollEnabled.get() ? null : getEventScrollBar(e);
    if (bar == null) {
      if (alternative != null) alternative.accept(e);
      return;
    }

    if (TouchScrollUtil.isBegin(e)) {
      verticalFling.registerBegin();
      horizontalFling.registerBegin();
    } else if (TouchScrollUtil.isUpdate(e)) {
      int value = bar.getValue();
      int delta = (int)TouchScrollUtil.getDelta(e);
      bar.setValue(value + delta);
      FlingManager fling = isHorizontalScroll(e) ? horizontalFling : verticalFling;
      fling.registerUpdate(delta);
    } else if (TouchScrollUtil.isEnd(e)) {
      verticalFling.start(getEventVerticalScrollBar(e), vertical);
      horizontalFling.start(getEventHorizontalScrollBar(e), horizontal);
    }

    e.consume();
  }


  private static Predicate<Integer> shouldStop(JScrollBar bar) {
    return (v) -> {
      return v - bar.getValue() != 0 || !bar.isShowing();
    };
  }


  private static class FlingManager {
    private long beginTime = 0L;
    private int lastDelta = 0;

    public void registerBegin() {
      beginTime = System.currentTimeMillis();
      lastDelta = 0;
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
      return TouchScroll.shouldStop(bar).or(v -> {
        return beginTime != 0L && (System.currentTimeMillis() - beginTime) > getFlingInterruptLag();
      });
    }

    public void start(JScrollBar bar, InertialAnimator animator) {
      if (bar != null && shouldStart()) {
        int initValue = bar.getValue();
        int targetValue = initValue + lastDelta * getFlingMultiplier();
        animator.start(initValue, targetValue, bar::setValue, shouldStop(bar), TouchAnimationSettings.SETTINGS);
      }
    }
  }

  enum TouchAnimationSettings implements AnimationSettings {
    SETTINGS {
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
}
