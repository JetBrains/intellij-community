/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;

/**
 * @author Sergey.Malenkov
 */
enum ScrollSource {
  MOUSE_WHEEL {
    private final RegistryValue ENABLED = Registry.get("idea.true.smooth.scrolling.interpolation.mouse.wheel");
    private final RegistryValue MIN_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.min");
    private final RegistryValue MAX_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.max");

    @Override
    boolean isInterpolationEnabled() {
      return INTERPOLATION_ENABLED.asBoolean() && ENABLED.asBoolean();
    }

    @Override
    int getInterpolationDelay(double rotation) {
      if (!isInterpolationEnabled()) return 0;
      int min = MIN_DELAY.asInteger();
      int max = MAX_DELAY.asInteger();
      return Math.max(min, Math.min(max, (int)Math.round(max * Math.abs(rotation))));
    }
  },
  TOUCHPAD {
    private final RegistryValue ENABLED = Registry.get("idea.true.smooth.scrolling.interpolation.precision.touchpad");
    private final RegistryValue DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.precision.touchpad.delay");

    @Override
    boolean isInterpolationEnabled() {
      return INTERPOLATION_ENABLED.asBoolean() && ENABLED.asBoolean();
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : DELAY.asInteger();
    }
  },
  SCROLLBAR {
    private final RegistryValue ENABLED = Registry.get("idea.true.smooth.scrolling.interpolation.scrollbar");
    private final RegistryValue DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.scrollbar.delay");

    @Override
    boolean isInterpolationEnabled() {
      return INTERPOLATION_ENABLED.asBoolean() && ENABLED.asBoolean();
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : DELAY.asInteger();
    }
  },
  UNKNOWN {
    private final RegistryValue ENABLED = Registry.get("idea.true.smooth.scrolling.interpolation.other");
    private final RegistryValue DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.other.delay");

    @Override
    boolean isInterpolationEnabled() {
      return INTERPOLATION_ENABLED.asBoolean() && ENABLED.asBoolean();
    }

    @Override
    int getInterpolationDelay(double rotation) {
      return !isInterpolationEnabled() ? 0 : DELAY.asInteger();
    }
  };

  private static final RegistryValue INTERPOLATION_ENABLED = Registry.get("idea.true.smooth.scrolling.interpolation");

  abstract boolean isInterpolationEnabled();

  abstract int getInterpolationDelay(double rotation);
}
