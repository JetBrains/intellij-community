// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface ColorPickerListener {

  /**
   * Color was changed by user.
   */
  void colorChanged(Color color);

  /**
   * Dialog was closed
   *
   * @param color resulting color or {@code null} if dialog was cancelled.
   */
  void closed(@Nullable Color color);
}
