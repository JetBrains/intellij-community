/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.ui;

/**
 * This class is designed to provide reasonable restrictions for UI-specific settings
 */
public final class UINumericRange {
  public static final UINumericRange SYSTEM_SETTINGS_SAVE_FILES_AFTER_IDLE_SEC = new UINumericRange(15, 1, 300);
  public static final UINumericRange EDITOR_QUICK_DOC_DELAY_MS = new UINumericRange(15, 0, 15000);
  public static final UINumericRange RECENT_FILES_LIMIT = new UINumericRange(50, 1, 5000);
  public static final UINumericRange MAX_CLIPBOARD_CONTENTS = new UINumericRange(5, 1, 500);
  public static final UINumericRange CUSTOM_SOFT_WRAP_INDENT= new UINumericRange(0, 0, 200);
  public static final UINumericRange ERROR_STRIPE_MARK_MIN_HEIGHT = new UINumericRange(2, 0, 125);
  public static final UINumericRange AUTOREPARSE_DELAY_MS = new UINumericRange(300, 0, 60000);

  public final int initial;
  public final int min;
  public final int max;

  public UINumericRange(int defaultValue, int minimumValue, int maximumValue) {
    if (minimumValue > maximumValue || defaultValue < minimumValue || defaultValue > maximumValue) {
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
