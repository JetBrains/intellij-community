// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class GlobalEditorScheme {
  public static @Nullable Color getColor(@NotNull ColorKey key) {
    return getGlobalScheme().getColor(key);
  }

  @NotNull
  private static EditorColorsScheme getGlobalScheme() {
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  public static @NotNull Color getDefaultBackground() {
    return getGlobalScheme().getDefaultBackground();
  }

  public static @NotNull Color getDefaultForeground() {
    return getGlobalScheme().getDefaultForeground();
  }

  public static Color getBackground(@NotNull TextAttributesKey key) {
    return getGlobalScheme().getAttributes(key).getBackgroundColor();
  }
}
