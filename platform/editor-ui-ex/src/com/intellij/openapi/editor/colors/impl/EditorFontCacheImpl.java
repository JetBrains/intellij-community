// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.impl.FontFamilyService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
public class EditorFontCacheImpl extends EditorFontCache {
  private static final Logger LOG = Logger.getInstance(EditorFontCacheImpl.class);

  private static final Map<TextAttribute, Integer> LIGATURES_ATTRIBUTES = Map.of(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);

  private final @NotNull Map<EditorFontType, Font> fonts = new EnumMap<>(EditorFontType.class);

  @Override
  public @NotNull Font getFont(@Nullable EditorFontType key) {
    synchronized (fonts) {
      if (fonts.isEmpty()) {
        initFonts();
      }

      EditorFontType fontType = Objects.requireNonNullElse(key, EditorFontType.PLAIN);
      Font font = fonts.get(fontType);
      assert font != null : "Font " + fontType + " not found.";
      UISettings uiSettings = UISettings.getInstance();
      if (uiSettings.getPresentationMode()) {
        return font.deriveFont(UISettingsUtils.with(uiSettings).getPresentationModeFontSize());
      }
      return font;
    }
  }

  @Override
  public void reset() {
    synchronized (fonts) {
      fonts.clear();
    }
  }

  protected EditorColorsScheme getFontCacheScheme() {
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  private void initFonts() {
    EditorColorsScheme scheme = getFontCacheScheme();
    FontPreferences preferences = scheme.getFontPreferences();
    String editorFontName = preferences.getFontFamily();
    float editorFontSize = scheme.getEditorFontSize2D();
    String fallbackName = getFallbackName(editorFontName);
    if (fallbackName != null) {
      editorFontName = fallbackName;
    }
    if (LOG.isDebugEnabled()) {
      String schemeName;
      try {
        schemeName = scheme.getName();
      } catch (Throwable th) {
        LOG.warn(th);
        schemeName = "unknown(th)";
      }
      LOG.debug(String.format(
        "Initializing fonts: scheme=%s, delegating=%b, fontName=%s, fontSize=%.2f",
        schemeName, preferences instanceof DelegatingFontPreferences, editorFontName, editorFontSize));
    }

    setFont(EditorFontType.PLAIN, editorFontName, Font.PLAIN, editorFontSize, preferences);
    setFont(EditorFontType.BOLD, editorFontName, Font.BOLD, editorFontSize, preferences);
    setFont(EditorFontType.ITALIC, editorFontName, Font.ITALIC, editorFontSize, preferences);
    setFont(EditorFontType.BOLD_ITALIC, editorFontName, Font.BOLD | Font.ITALIC, editorFontSize, preferences);

    FontPreferences consolePreferences = scheme.getConsoleFontPreferences();
    String consoleFontName = scheme.getConsoleFontName();
    float consoleFontSize = scheme.getConsoleFontSize2D();

    setFont(EditorFontType.CONSOLE_PLAIN, consoleFontName, Font.PLAIN, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_BOLD, consoleFontName, Font.BOLD, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_ITALIC, consoleFontName, Font.ITALIC, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_BOLD_ITALIC, consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize, consolePreferences);
  }

  private void setFont(EditorFontType fontType,
                       String familyName,
                       int style,
                       float fontSize,
                       FontPreferences fontPreferences) {
    Font baseFont = FontFamilyService.getFont(familyName, fontPreferences.getRegularSubFamily(), fontPreferences.getBoldSubFamily(),
                                              style, fontSize);
    fonts.put(fontType, deriveFontWithLigatures(baseFont, fontPreferences.useLigatures()));
  }

  private static @Nullable String getFallbackName(@NotNull String fontName) {
    Font plainFont = new Font(fontName, Font.PLAIN, 12);
    if (plainFont.getFamily().equals("Dialog") && !("Dialog".equals(fontName) || fontName.startsWith("Dialog."))) {
      return AppEditorFontOptions.getInstance().getFontPreferences().getFontFamily();
    }
    return null;
  }

  public static @NotNull Font deriveFontWithLigatures(@NotNull Font font, boolean enableLigatures) {
    return enableLigatures ? font.deriveFont(LIGATURES_ATTRIBUTES) : font;
  }
}
