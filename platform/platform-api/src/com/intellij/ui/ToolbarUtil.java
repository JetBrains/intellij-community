// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ToolbarUtil {
  public static void setCustomTitleBar(@NotNull Window window,
                                       @NotNull JRootPane rootPane,
                                       @NotNull Consumer<? super Runnable> onDispose) {
    ToolbarService.getInstance().setCustomTitleBar(window, rootPane, onDispose);
  }

  public static void setTransparentTitleBar(@NotNull Window window,
                                            @NotNull JRootPane rootPane,
                                            Consumer<? super Runnable> onDispose) {
    ToolbarService.getInstance().setTransparentTitleBar(window, rootPane, onDispose);
  }

  public static void setTransparentTitleBar(@NotNull Window window,
                                            @NotNull JRootPane rootPane,
                                            @Nullable Supplier<? extends FullScreenSupport> handlerProvider,
                                            Consumer<? super Runnable> onDispose) {
    ToolbarService.getInstance().setTransparentTitleBar(window, rootPane, handlerProvider, onDispose);
  }
}
