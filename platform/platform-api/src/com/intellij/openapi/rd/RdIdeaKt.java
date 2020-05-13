// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd;

import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.paint.LinePainter2D;
import com.jetbrains.rd.util.reactive.IPropertyView;
import com.jetbrains.rd.util.reactive.ISource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class RdIdeaKt {
  private RdIdeaKt() {}

  /**
   * @deprecated Use version from `SwingReactiveEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static ISource<MouseEvent> mouseMoved(IdeGlassPane glassPane) {
    return GraphicsExKt.mouseMoved(glassPane);
  }

  /**
   * @deprecated Use version from `SwingReactiveEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static ISource<@Nullable Component> childAtMouse(IdeGlassPane glassPane, Container container) {
    return GraphicsExKt.childAtMouse(glassPane,container);
  }

  /**
   * @deprecated Use version from `SwingReactiveEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static IPropertyView<@Nullable Component> childAtMouse(JComponent component) {
    return GraphicsExKt.childAtMouse(component);
  }

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

  /**
   * @deprecated Use version from `GraphicsExKt`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void paint2DLine(Graphics2D graphics2D, double x1, double y1, double x2, double y2, LinePainter2D.StrokeType strokeType, double strokeWidth, Color color) {
    GraphicsExKt.paint2DLine(graphics2D, x1, y1, x2, y2, strokeType, strokeWidth, color);
  }
}
