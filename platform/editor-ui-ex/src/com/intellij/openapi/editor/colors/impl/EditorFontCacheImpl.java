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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class EditorFontCacheImpl extends EditorFontCache {
  @NotNull private final Map<EditorFontType, Font> myFonts = new EnumMap<>(EditorFontType.class);

  @Override
  @NotNull
  public Font getFont(@Nullable EditorFontType key) {
    synchronized (myFonts) {
      if (myFonts.isEmpty()) {
        initFonts();
      }
      EditorFontType fontType = ObjectUtils.notNull(key, EditorFontType.PLAIN);
      final Font font = myFonts.get(fontType);
      assert font != null : "Font " + fontType + " not found.";
      UISettings uiSettings = UISettings.getInstance();
      if (uiSettings.getPresentationMode()) {
        return new Font(font.getName(), font.getStyle(), uiSettings.getPresentationModeFontSize());
      }
      return font;
    }
  }

  @Override
  public void reset() {
    synchronized (myFonts) {
      myFonts.clear();
    }
  }

  protected EditorColorsScheme getFontCacheScheme() {
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  private void initFonts() {
    EditorColorsScheme scheme = getFontCacheScheme();
    FontPreferences preferences = scheme.getFontPreferences();
    String editorFontName = preferences.getFontFamily();
    int editorFontSize = scheme.getEditorFontSize();
    String fallbackName = getFallbackName(editorFontName, editorFontSize);
    if (fallbackName != null) {
      editorFontName = fallbackName;
    }

    setFont(EditorFontType.PLAIN, editorFontName, Font.PLAIN, editorFontSize, preferences);
    setFont(EditorFontType.BOLD, editorFontName, Font.BOLD, editorFontSize, preferences);
    setFont(EditorFontType.ITALIC, editorFontName, Font.ITALIC, editorFontSize, preferences);
    setFont(EditorFontType.BOLD_ITALIC, editorFontName, Font.BOLD | Font.ITALIC, editorFontSize, preferences);

    FontPreferences consolePreferences = scheme.getConsoleFontPreferences();
    String consoleFontName = scheme.getConsoleFontName();
    int consoleFontSize = scheme.getConsoleFontSize();

    setFont(EditorFontType.CONSOLE_PLAIN, consoleFontName, Font.PLAIN, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_BOLD, consoleFontName, Font.BOLD, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_ITALIC, consoleFontName, Font.ITALIC, consoleFontSize, consolePreferences);
    setFont(EditorFontType.CONSOLE_BOLD_ITALIC, consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize, consolePreferences);
  }

  private void setFont(EditorFontType fontType,
                       String familyName,
                       int style,
                       int fontSize,
                       FontPreferences fontPreferences) {
    myFonts.put(fontType,
                FontFamilyService.getFont(familyName, fontPreferences.getRegularSubFamily(), fontPreferences.getBoldSubFamily(),
                                          style, fontSize));
  }

  @Nullable
  private static String getFallbackName(@NotNull String fontName, int fontSize) {
    Font plainFont = new Font(fontName, Font.PLAIN, fontSize);
    if (plainFont.getFamily().equals("Dialog") && !("Dialog".equals(fontName) || fontName.startsWith("Dialog."))) {
      FontPreferences appPrefs = AppEditorFontOptions.getInstance().getFontPreferences();
      return appPrefs.getFontFamily();
    }
    return null;
  }
}
