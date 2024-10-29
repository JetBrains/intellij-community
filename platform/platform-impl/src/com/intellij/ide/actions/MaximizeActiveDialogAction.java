// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static java.lang.Math.abs;

@ApiStatus.Internal
public final class MaximizeActiveDialogAction extends WindowAction {
  private static final String NORMAL_BOUNDS = "NORMAL_BOUNDS";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    @Nullable Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Window window = ComponentUtil.getWindow(component);
    if (!(window instanceof JDialog)) return;
    doMaximize((JDialog)window);
  }

  public static void doMaximize(JDialog dialog) {
    if (canBeMaximized(dialog)) {
      maximize(dialog);
    }
    else if (canBeNormalized(dialog)) {
      normalize(dialog);
    }
  }

  public static boolean canBeMaximized(JDialog dialog) {
    JRootPane rootPane = dialog != null && dialog.isResizable() ? dialog.getRootPane() : null;
    if (rootPane == null) return false;
    return !almostEquals(ScreenUtil.getScreenRectangle(dialog), dialog.getBounds());
  }

  public static void maximize(JDialog dialog) {
    if (!canBeMaximized(dialog)) return;
    dialog.getRootPane().putClientProperty(NORMAL_BOUNDS, dialog.getBounds());
    dialog.setBounds(ScreenUtil.getScreenRectangle(dialog));
  }

  public static boolean canBeNormalized(JDialog dialog) {
    JRootPane rootPane = dialog != null && dialog.isResizable() ? dialog.getRootPane() : null;
    if (rootPane == null) return false;
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(dialog);
    return almostEquals(dialog.getBounds(), screenRectangle) && rootPane.getClientProperty(NORMAL_BOUNDS) instanceof Rectangle;
  }

  private static boolean almostEquals(@NotNull Rectangle r1, @NotNull Rectangle r2) {
    int tolerance = Registry.intValue("ide.dialog.maximize.tolerance", 10);
    return abs(r1.x - r2.x) <= tolerance && abs(r1.y - r2.y) <= tolerance && abs(r1.width - r2.width) <= tolerance && abs(r1.height - r2.height) <= tolerance;
  }

  public static void normalize(JDialog dialog) {
    if (!canBeNormalized(dialog)) return;
    JRootPane rootPane = dialog.getRootPane();
    Object value = rootPane.getClientProperty(NORMAL_BOUNDS);
    if (value instanceof Rectangle bounds) {
      ScreenUtil.fitToScreen(bounds);
      dialog.setBounds(bounds);
      rootPane.putClientProperty(NORMAL_BOUNDS, null);
    }
  }
}
