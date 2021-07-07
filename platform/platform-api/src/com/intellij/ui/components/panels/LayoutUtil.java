// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class LayoutUtil {
  /**
   * @param component the component whose size is calculated
   * @return the preferred size of the given component based on its maximum size
   */
  static @NotNull Dimension getPreferredSize(@NotNull Component component) {
    Dimension size = component.getPreferredSize();
    if (size == null) return new Dimension(); // rare
    if (component.isMaximumSizeSet()) {
      Dimension max = component.getMaximumSize();
      if (max != null) {
        if (size.width > max.width) size.width = max.width;
        if (size.height > max.height) size.height = max.height;
      }
    }
    return size;
  }
}
