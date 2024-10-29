// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.images.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class PlatformImagesIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PlatformImagesIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, PlatformImagesIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 75x86 */ public static final @NotNull Icon ThumbnailBlank = load("org/intellij/images/icons/ThumbnailBlank.png", 0, 2);
  /** 75x82 */ public static final @NotNull Icon ThumbnailDirectory = load("org/intellij/images/icons/ThumbnailDirectory.png", 0, 0);
  /** 13x13 */ public static final @NotNull Icon ThumbnailToolWindow = load("org/intellij/images/icons/ThumbnailToolWindow.svg", 32430216, 2);
  /** 16x16 */ public static final @NotNull Icon ToggleTransparencyChessboard = load("org/intellij/images/icons/expui/chessboard.svg", "org/intellij/images/icons/ToggleTransparencyChessboard.svg", -1782881253, 2);
}
