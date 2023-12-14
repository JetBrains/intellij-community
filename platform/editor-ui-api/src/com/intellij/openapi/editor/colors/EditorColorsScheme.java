// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeMetaInfo;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface EditorColorsScheme extends Cloneable, TextAttributesScheme, Scheme, SchemeMetaInfo {
  @NonNls String DEFAULT_SCHEME_NAME = "Default";

  void setName(String name);

  void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes);

  TextAttributes getAttributes(@Nullable TextAttributesKey key, boolean useDefaults);

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

  @NlsSafe
  String getEditorFontName();

  void setEditorFontName(String fontName);

  int getEditorFontSize();
  /**
   * Floating-point version of {@link #getEditorFontSize()}
   */
  default float getEditorFontSize2D() {
    return getEditorFontSize();
  }

  /**
   * Sets font size. Note, that this method checks that {@code fontSize} is within bounds and could change it if it is
   * more than {@link com.intellij.application.options.EditorFontsConstants#getMaxEditorFontSize()} or less than
   * {@link com.intellij.application.options.EditorFontsConstants#getMinEditorFontSize()}
   * @see com.intellij.application.options.EditorFontsConstants
   */
  void setEditorFontSize(int fontSize);
  /**
   * Floating-point version of {@link #setEditorFontSize(int)}
   */
  default void setEditorFontSize(float fontSize) {
    setEditorFontSize((int)(fontSize + 0.5));
  }

  /**
   * @deprecated Quick documentation component's font size is stored in application level property, and can be obtained
   * using {@link com.intellij.codeInsight.documentation.DocumentationComponent#getQuickDocFontSize()}.
   */
  @Deprecated(forRemoval = true)
  default FontSize getQuickDocFontSize() { return FontSize.SMALL; }

  /**
   * @deprecated Quick documentation component's font size is stored in application level property, and can be set
   * using {@link com.intellij.codeInsight.documentation.DocumentationComponent#setQuickDocFontSize(FontSize)}.
   */
  @Deprecated(forRemoval = true)
  default void setQuickDocFontSize(@NotNull FontSize fontSize) {}

  @NotNull
  Font getFont(EditorFontType key);

  float getLineSpacing();

  /**
   * Sets line spacing. Note, that this method checks that {@code lineSpacing} is within bounds and could change it if it is
   * more than {@link com.intellij.application.options.EditorFontsConstants#getMaxEditorLineSpacing()} or less than
   * {@link com.intellij.application.options.EditorFontsConstants#getMinEditorLineSpacing()}
   * <p>
   * Currently, changing line spacing does not change the editor's scrolling position (in pixels), so the viewport's logical
   * location can change as a result.
   *
   * @see com.intellij.application.options.EditorFontsConstants
   */
  void setLineSpacing(float lineSpacing);

  boolean isUseLigatures();

  void setUseLigatures(boolean useLigatures);

  Object clone();

  /**
   * @return    console font preferences to use
   * @see #getFontPreferences()
   */
  @NotNull
  FontPreferences getConsoleFontPreferences();
  void setConsoleFontPreferences(@NotNull FontPreferences preferences);

  default void setUseEditorFontPreferencesInConsole() {}
  default boolean isUseEditorFontPreferencesInConsole() {return false;}

  default void setUseAppFontPreferencesInEditor() {}
  default boolean isUseAppFontPreferencesInEditor() {return false;}

  @NlsSafe
  String getConsoleFontName();

  void setConsoleFontName(String fontName);

  int getConsoleFontSize();
  /**
   * Floating-point version of {@link #getConsoleFontSize()}
   */
  default float getConsoleFontSize2D() {
    return getConsoleFontSize();
  }
  void setConsoleFontSize(int fontSize);
  /**
   * Floating-point version of {@link #setConsoleFontSize(int)}
   */
  default void setConsoleFontSize(float fontSize) {
    setConsoleFontSize((int)(fontSize + 0.5));
  }

  float getConsoleLineSpacing();
  void setConsoleLineSpacing(float lineSpacing);

  void readExternal(Element parentNode);

  @ApiStatus.Internal
  boolean isReadOnly();
}
