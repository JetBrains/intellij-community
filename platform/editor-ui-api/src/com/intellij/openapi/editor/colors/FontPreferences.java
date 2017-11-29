/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class FontPreferences {
  public final static @NonNls @NotNull String DEFAULT_FONT_NAME = getDefaultFontName();
  public final static int DEFAULT_FONT_SIZE = FontSize.SMALL.getSize();

  public final static float DEFAULT_LINE_SPACING = 1.0f;
  public final static String FALLBACK_FONT_FAMILY         = "Monospaced";
  public final static String MAC_OS_DEFAULT_FONT_FAMILY   = "Menlo";
  public final static String LINUX_DEFAULT_FONT_FAMILY    = "DejaVu Sans Mono";
  public final static String WINDOWS_DEFAULT_FONT_FAMILY  = FALLBACK_FONT_FAMILY;

  @NotNull
  public List<String> getEffectiveFontFamilies() {
    return Collections.emptyList();
  }

  @NotNull
  public List<String> getRealFontFamilies() {
    return Collections.emptyList();
  }

  @NotNull
  public String getFontFamily() {
    return FALLBACK_FONT_FAMILY;
  }

  public int getSize(@NotNull String fontFamily) {
    return DEFAULT_FONT_SIZE;
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

  /**
   * There is a possible case that particular font family is not available at particular environment (e.g. Monaco under *nix).
   * However, java environment tries to mask that via 'Dialog' fonts, i.e. when we try to create font like
   * {@code new Font("Monaco", style, size)}, it creates a font object which has font family "Monaco" but is a "Dialog" font.
   * <p/>
   * That's why we have a special check for such a situation.
   *
   * @param fontName        font family name to check
   * @param fontSize        target font size
   * @param fallbackScheme  colors scheme to use for fallback fonts retrieval (if necessary);
   * @return                fallback font family to use if font family with the given name is not registered at current environment;
   *                        {@code null} if font family with the given name is registered at the current environment
   */
  @Nullable
  public static String getFallbackName(@NotNull String fontName, int fontSize, @Nullable EditorColorsScheme fallbackScheme) {
    Font plainFont = new Font(fontName, Font.PLAIN, fontSize);
    if (plainFont.getFamily().equals("Dialog") && !("Dialog".equals(fontName) || fontName.startsWith("Dialog."))) {
      return fallbackScheme == null ? DEFAULT_FONT_NAME : fallbackScheme.getEditorFontName();
    }
    return null;
  }

  public static String getDefaultFontName() {
    if (SystemInfo.isWindows) return WINDOWS_DEFAULT_FONT_FAMILY;
    if (SystemInfo.isMacOSSnowLeopard) return MAC_OS_DEFAULT_FONT_FAMILY;
    if (SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless()) {
      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
        if (LINUX_DEFAULT_FONT_FAMILY.equals(font.getName())) {
          return font.getFontName();
        }
      }
    }
    return FALLBACK_FONT_FAMILY;
  }
}
