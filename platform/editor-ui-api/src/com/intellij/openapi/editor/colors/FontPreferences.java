// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class FontPreferences {
  public static final @NlsSafe @NotNull String DEFAULT_FONT_NAME = getDefaultFontName();
  public static final String JETBRAINS_MONO = "JetBrains Mono";
  public static final int DEFAULT_FONT_SIZE = SystemInfo.isWindows || JETBRAINS_MONO.equalsIgnoreCase(DEFAULT_FONT_NAME) ? 13 : 12;

  public static final float DEFAULT_LINE_SPACING = 1.2f;
  public static final String FALLBACK_FONT_FAMILY         = "Monospaced";
  public static final String MAC_OS_DEFAULT_FONT_FAMILY   = "Menlo";
  public static final String LINUX_DEFAULT_FONT_FAMILY    = "DejaVu Sans Mono";
  public static final String WINDOWS_DEFAULT_FONT_FAMILY  = "Consolas";

  public @NotNull List<@NlsSafe String> getEffectiveFontFamilies() {
    return Collections.emptyList();
  }

  public @NotNull List<@NlsSafe String> getRealFontFamilies() {
    return Collections.emptyList();
  }

  public @NotNull @NlsSafe String getFontFamily() {
    return FALLBACK_FONT_FAMILY;
  }

  public @NlsSafe @Nullable String getRegularSubFamily() {
    return null;
  }

  public @NlsSafe @Nullable String getBoldSubFamily() {
    return null;
  }

  public int getSize(@NotNull String fontFamily) {
    return DEFAULT_FONT_SIZE;
  }

  /**
   * Floating-point version of {@link #getSize(String)}
   */
  public float getSize2D(@NotNull String fontFamily) {
    return getSize(fontFamily);
  }

  public void copyTo(@NotNull FontPreferences preferences) {
  }

  public boolean useLigatures() {
    return false;
  }

  public boolean hasSize(@NotNull String fontName) {
    return false;
  }

  public float getLineSpacing() {
    return DEFAULT_LINE_SPACING;
  }

  public static @NlsSafe String getDefaultFontName() {
    if (SystemInfo.isJetBrainsJvm) {
      return JETBRAINS_MONO;
    }
    if (SystemInfo.isWindows) {
      return WINDOWS_DEFAULT_FONT_FAMILY;
    }
    if (SystemInfoRt.isMac) {
      return MAC_OS_DEFAULT_FONT_FAMILY;
    }
    if (SystemInfoRt.isLinux && !GraphicsEnvironment.isHeadless() && !ApplicationManager.getApplication().isCommandLine()) {
      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
        if (LINUX_DEFAULT_FONT_FAMILY.equals(font.getName())) {
          return font.getFontName();
        }
      }
    }
    return FALLBACK_FONT_FAMILY;
  }
}
