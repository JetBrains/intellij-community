// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

/**
 * This class is designed to provide reasonable restrictions for UI-specific settings
 */
public final class UINumericRange {
  public final int initial;
  public final int min;
  public final int max;

  public UINumericRange(int defaultValue, int minimumValue, int maximumValue) {
    if (defaultValue < minimumValue || defaultValue > maximumValue) {
      throw new IllegalArgumentException("Wrong range values: [" + minimumValue + ".." + defaultValue + ".." + maximumValue + "]");
    }
    initial = defaultValue;
    min = minimumValue;
    max = maximumValue;
  }

  public int fit(int value) {
    return Math.max(min, Math.min(max, value));
  }

  @Override
  public String toString() {
    return "[" + min + ".." + initial + ".." + max + "]";
  }
}
