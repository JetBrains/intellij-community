// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;

public enum EditorFontType {
  PLAIN,
  BOLD,
  ITALIC,
  BOLD_ITALIC,
  CONSOLE_PLAIN,
  CONSOLE_BOLD,
  CONSOLE_ITALIC,
  CONSOLE_BOLD_ITALIC;

  /**
   * @param type a font type to convert
   * @return a console variant of the specified font type
   */
  public static @Nullable EditorFontType getConsoleType(@Nullable EditorFontType type) {
    return type == PLAIN ? CONSOLE_PLAIN :
           type == ITALIC ? CONSOLE_ITALIC :
           type == BOLD ? CONSOLE_BOLD :
           type == BOLD_ITALIC ? CONSOLE_BOLD_ITALIC : type;
  }

  /**
   * @return a plain font according to the global colors scheme
   */
  public static @NotNull Font getGlobalPlainFont() {
    return PLAIN.getGlobalFont();
  }

  /**
   * @return a font of this type according to the global colors scheme
   */
  public @NotNull Font getGlobalFont() {
    return EditorColorsManager.getInstance().getGlobalScheme().getFont(this);
  }

  public static @NotNull EditorFontType forJavaStyle(@JdkConstants.FontStyle int style) {
    return switch (style) {
      case Font.BOLD -> BOLD;
      case Font.ITALIC -> ITALIC;
      case Font.BOLD | Font.ITALIC -> BOLD_ITALIC;
      default -> PLAIN;
    };
  }
}
