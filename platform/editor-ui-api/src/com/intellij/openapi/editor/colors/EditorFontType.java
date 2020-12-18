// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

public enum EditorFontType {
  PLAIN,
  BOLD,
  ITALIC,
  BOLD_ITALIC,
  CONSOLE_PLAIN,
  CONSOLE_BOLD,
  CONSOLE_ITALIC,
  CONSOLE_BOLD_ITALIC;

  private static final Map<EditorFontType, EditorFontType> ourConsoleTypes = new HashMap<>();
  static {
    ourConsoleTypes.put(PLAIN, CONSOLE_PLAIN);
    ourConsoleTypes.put(ITALIC, CONSOLE_ITALIC);
    ourConsoleTypes.put(BOLD_ITALIC, CONSOLE_BOLD_ITALIC);
    ourConsoleTypes.put(BOLD, CONSOLE_BOLD);
  }

  public static EditorFontType getConsoleType(EditorFontType fontType) {
    return ObjectUtils.chooseNotNull(ourConsoleTypes.get(fontType), fontType);
  }

  public @NotNull Font getFont(@NotNull Editor editor) {
    return editor.getColorsScheme().getFont(this);
  }

  public @NotNull Font getGlobalFont() {
    return EditorColorsManager.getInstance().getGlobalScheme().getFont(this);
  }
}
