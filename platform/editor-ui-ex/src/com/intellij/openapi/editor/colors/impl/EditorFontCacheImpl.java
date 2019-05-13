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
    String editorFontName = scheme.getFontPreferences().getFontFamily();
    int editorFontSize = scheme.getEditorFontSize();
    String fallbackName = getFallbackName(editorFontName, editorFontSize);
    if (fallbackName != null) {
      editorFontName = fallbackName;
    }

    Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
    Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
    Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
    Font boldItalicFont = new Font(editorFontName, Font.BOLD | Font.ITALIC, editorFontSize);

    myFonts.put(EditorFontType.PLAIN, plainFont);
    myFonts.put(EditorFontType.BOLD, boldFont);
    myFonts.put(EditorFontType.ITALIC, italicFont);
    myFonts.put(EditorFontType.BOLD_ITALIC, boldItalicFont);

    String consoleFontName = scheme.getConsoleFontName();
    int consoleFontSize = scheme.getConsoleFontSize();

    Font consolePlainFont = new Font(consoleFontName, Font.PLAIN, consoleFontSize);
    Font consoleBoldFont = new Font(consoleFontName, Font.BOLD, consoleFontSize);
    Font consoleItalicFont = new Font(consoleFontName, Font.ITALIC, consoleFontSize);
    Font consoleBoldItalicFont = new Font(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize);

    myFonts.put(EditorFontType.CONSOLE_PLAIN, consolePlainFont);
    myFonts.put(EditorFontType.CONSOLE_BOLD, consoleBoldFont);
    myFonts.put(EditorFontType.CONSOLE_ITALIC, consoleItalicFont);
    myFonts.put(EditorFontType.CONSOLE_BOLD_ITALIC, consoleBoldItalicFont);
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
