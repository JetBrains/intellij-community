// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.registry.Registry;

enum ScrollSource {
  MOUSE_WHEEL {
    @Override
    boolean isInterpolationEnabled() {
      return isInterpolationEnabledByRegistry() && Registry.is("idea.true.smooth.scrolling.interpolation.mouse.wheel", true);
    }

    @Override
    int getInterpolationDelay(double rotation) {
      if (!isInterpolationEnabled()) return 0;
      int min = Registry.intValue("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.min", 60);
      int max = Registry.intValue("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.max", 140);
      return Math.max(min, Math.min(max, (int)Math.round(max * Math.abs(rotation))));
    }
  },
  TOUCHPAD {
    @Override
    boolean isInterpolationEnabled() {
      return isInterpolationEnabledByRegistry() && Registry.is("idea.true.smooth.scrolling.interpolation.precision.touchpad", true);
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : Registry.intValue("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.min", 20);
    }
  },
  SCROLLBAR {
    @Override
    boolean isInterpolationEnabled() {
      return isInterpolationEnabledByRegistry() && Registry.is("idea.true.smooth.scrolling.interpolation.scrollbar", true);
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : Registry.intValue("idea.true.smooth.scrolling.interpolation.scrollbar.delay", 30);
    }
  },
  UNKNOWN {
    @Override
    boolean isInterpolationEnabled() {
      return isInterpolationEnabledByRegistry() && Registry.is("idea.true.smooth.scrolling.interpolation.other", true);
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : Registry.intValue("idea.true.smooth.scrolling.interpolation.other.delay", 120);
    }
  };

  private static boolean isInterpolationEnabledByRegistry() {
    return Registry.is("idea.true.smooth.scrolling.interpolation", false);
  }

  abstract boolean isInterpolationEnabled();

  abstract int getInterpolationDelay(double rotation);
}
