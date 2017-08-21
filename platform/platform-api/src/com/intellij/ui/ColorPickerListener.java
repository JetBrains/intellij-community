/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface ColorPickerListener {
  @Deprecated
  ColorPickerListener[] EMPTY_ARRAY = new ColorPickerListener[0];

  /**
   * Color was changed by user
   * @param color
   */
  void colorChanged(Color color);

  /**
   * Dialog was closed
   * @param color resulting color or {@code null} if dialog was cancelled
   */
  void closed(@Nullable Color color);
}
