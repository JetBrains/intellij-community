// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class JavaUIIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, JavaUIIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, JavaUIIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 40x40 */ public static final @NotNull Icon IdeaUltimatePromo = load("icons/idea-ultimate-promo.svg", 597316087, 0);
  /** 16x16 */ public static final @NotNull Icon SpringPromo = load("icons/newui/spring-promo.svg", "icons/spring-promo.svg", -1690195316, 0);
}
