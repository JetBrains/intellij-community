// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseWheelEvent;

public final class SmoothScrollUtil {
  public static @Nullable
  JScrollBar getEventScrollBar(@NotNull MouseWheelEvent e) {
    return isHorizontalScroll(e) ? getEventHorizontalScrollBar(e) : getEventVerticalScrollBar(e);
  }

  public static @Nullable
  JScrollBar getEventHorizontalScrollBar(@NotNull MouseWheelEvent e) {
    JScrollPane scroller = (JScrollPane)e.getComponent();
    return scroller == null ? null : scroller.getHorizontalScrollBar();
  }

  public static @Nullable
  JScrollBar getEventVerticalScrollBar(@NotNull MouseWheelEvent e) {
    JScrollPane scroller = (JScrollPane)e.getComponent();
    return scroller == null ? null : scroller.getVerticalScrollBar();
  }

  public static boolean isHorizontalScroll(@NotNull MouseWheelEvent e) {
    return e.isShiftDown();
  }
}
