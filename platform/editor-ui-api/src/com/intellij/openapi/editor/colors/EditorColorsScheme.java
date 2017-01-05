/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeMetaInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface EditorColorsScheme extends Cloneable, TextAttributesScheme, Scheme, SchemeMetaInfo {
  @NonNls String DEFAULT_SCHEME_NAME = "Default";

  void setName(String name);

  void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes);

  @NotNull
  Color getDefaultBackground();
  @NotNull
  Color getDefaultForeground();

  @Nullable
  Color getColor(ColorKey key);
  void setColor(ColorKey key, Color color);

  /**
   * The IDE has allowed to configure only a single font family for a while. However, that doesn't handle a situation when
   * that font family is unable to display particular char - fallback font family was chosen randomly from the whole collection
   * of all registered fonts.
   * <p/>
   * Now it's possible to specify more than one font, i.e. directly indicated 'fallback fonts sequence' (see {@link FontPreferences}).
   * However, old single font-based API is still here in order to preserve backward compatibility ({@link #getEditorFontName()} and
   * {@link #getEditorFontSize()}). I.e. those methods are just re-written in order to use {@link FontPreferences} object exposed
   * by this method.
   *
   * @return    font preferences to use
   */
  @NotNull
  FontPreferences getFontPreferences();
  void setFontPreferences(@NotNull FontPreferences preferences);

  String getEditorFontName();
  void setEditorFontName(String fontName);

  int getEditorFontSize();

  /**
   * Sets font size. Note, that this method checks that {@code fontSize} is within bounds and could change it if it is
   * more than {@code com.intellij.application.options.EditorFontsConstants.getMaxEditorFontSize()} or less than
   * {@code com.intellij.application.options.EditorFontsConstants.getMinEditorFontSize()}
   * @see com.intellij.application.options.EditorFontsConstants
   */
  void setEditorFontSize(int fontSize);

  @Deprecated
  FontSize getQuickDocFontSize();

  @Deprecated
  void setQuickDocFontSize(@NotNull FontSize fontSize);

  Font getFont(EditorFontType key);
  void setFont(EditorFontType key, Font font);

  float getLineSpacing();

  /**
   * Sets line spacing. Note, that this method checks that {@code lineSpacing} is within bounds and could change it if it is
   * more than {@code com.intellij.application.options.EditorFontsConstants.getMaxEditorLineSpacing()} or less than
   * {@code com.intellij.application.options.EditorFontsConstants.getMinEditorLineSpacing()}
   * @see com.intellij.application.options.EditorFontsConstants
   */
  void setLineSpacing(float lineSpacing);

  Object clone();

  /**
   * @return    console font preferences to use
   * @see #getFontPreferences()
   */
  @NotNull
  FontPreferences getConsoleFontPreferences();
  void setConsoleFontPreferences(@NotNull FontPreferences preferences);

  String getConsoleFontName();
  void setConsoleFontName(String fontName);

  int getConsoleFontSize();
  void setConsoleFontSize(int fontSize);

  float getConsoleLineSpacing();
  void setConsoleLineSpacing(float lineSpacing);

  void readExternal(Element parentNode);
}
