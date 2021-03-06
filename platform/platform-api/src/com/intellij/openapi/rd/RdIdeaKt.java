// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd;

import com.intellij.ui.paint.LinePainter2D;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

public final class RdIdeaKt {
  private RdIdeaKt() {}

  /**
   * @deprecated Use version from `GraphicsExKt`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void fill2DRect(Graphics2D graphics2D, Rectangle rect, Color color) {
    GraphicsExKt.fill2DRect(graphics2D, rect, color);
  }

  /**
   * @deprecated Use version from `GraphicsExKt`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void paint2DLine(Graphics2D graphics2D, Point from, Point to, LinePainter2D.StrokeType strokeType, double strokeWidth, Color color) {
    GraphicsExKt.paint2DLine(graphics2D, from, to, strokeType, strokeWidth, color);
  }
}
