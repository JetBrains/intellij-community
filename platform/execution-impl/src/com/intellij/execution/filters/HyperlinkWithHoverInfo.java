// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface HyperlinkWithHoverInfo extends HyperlinkInfo {
  /**
   * Gets called when the mouse cursor enters the link's bounds.
   * @param hostComponent terminal/console component containing the link
   * @param linkBounds link's bounds relative to {@code hostComponent}
   */
  void onMouseEntered(@NotNull JComponent hostComponent, @NotNull Rectangle linkBounds);
  /**
   * Gets called when the mouse cursor exits the link's bounds.
   */
  void onMouseExited();
}
