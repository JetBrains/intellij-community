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

import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Utility class which holds collection of font families and theirs sizes.
 * <p/>
 * The basic idea is to allow end-user to configure not a single font but fonts list instead - every time particular font is unable
 * to display particular char, next font is tried. This is an improvement over an old approach when it was possible to configure
 * only a single font family. Fallback fonts were chosen randomly when that font family was unable to display particular char then.
 *
 * @author Denis Zhdanov
 * @since 12/20/12 9:37 PM
 */
public class FontPreferences {

  @NonNls @NotNull public static final String DEFAULT_FONT_NAME = getDefaultFontName();
  public static final                  int    DEFAULT_FONT_SIZE = FontSize.SMALL.getSize();

  @NotNull private final TObjectIntHashMap<String> myFontSizes    = new TObjectIntHashMap<>();
  @NotNull private final List<String> myEffectiveFontFamilies = ContainerUtilRt.newArrayList();
  @NotNull private final List<String> myRealFontFamilies = ContainerUtilRt.newArrayList();
  
  private boolean myUseLigatures;

  @Nullable private Runnable myChangeListener;

  /**
   * Font size to use by default. Default value is {@link #DEFAULT_FONT_SIZE}.
   */
  private int myTemplateFontSize = DEFAULT_FONT_SIZE;

  public void setChangeListener(@Nullable Runnable changeListener) {
    myChangeListener = changeListener;
  }

  @Nullable
  public Runnable getChangeListener() {
    return myChangeListener;
  }

  public void clear() {
    myEffectiveFontFamilies.clear();
    myRealFontFamilies.clear();
    myFontSizes.clear();
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  public void clearFonts() {
    myEffectiveFontFamilies.clear();
    myRealFontFamilies.clear();
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  public boolean hasSize(@NotNull String fontName) {
    return myFontSizes.containsKey(fontName);
  }

  public int getSize(@NotNull String fontFamily) {
    int result = myFontSizes.get(fontFamily);
    if (result <= 0) {
      result = myTemplateFontSize;
    }
    return result > 0 ? result : DEFAULT_FONT_SIZE;
  }

  public void setSize(@NotNull String fontFamily, int size) {
    myFontSizes.put(fontFamily, size);
    myTemplateFontSize = size;
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  /**
   * This method might return results different from {@link #getRealFontFamilies()} when
   * {@link #getFallbackName(String, int, EditorColorsScheme) a font family unavailable at current environment}
   * has been {@link #register(String, int) registered} at the current font preferences object.
   * <p/>
   * Effective fonts will hold fallback values for such font families then (exposed by the current method), 'real fonts' will
   * be available via {@link #getRealFontFamilies()}.
   *
   * @return    effective font families to use
   */
  @NotNull
  public List<String> getEffectiveFontFamilies() {
    return myEffectiveFontFamilies;
  }

  /**
   * @return    'real' font families
   * @see #getEffectiveFontFamilies()
   */
  @NotNull
  public List<String> getRealFontFamilies() {
    return myRealFontFamilies;
  }

  public void register(@NotNull String fontFamily, int size) {
    String fallbackFontFamily = getFallbackName(fontFamily, size, null);
    if (!myRealFontFamilies.contains(fontFamily)) {
      myRealFontFamilies.add(fontFamily);
    }
    String effectiveFontFamily = fallbackFontFamily == null ? fontFamily : fallbackFontFamily;
    if (!myEffectiveFontFamilies.contains(effectiveFontFamily)) {
      myEffectiveFontFamilies.add(effectiveFontFamily);
    }
    setSize(fontFamily, size);
  }

  /**
   * @return first element of the {@link #getEffectiveFontFamilies() registered font families} (if any);
   *         {@link #DEFAULT_FONT_NAME} otherwise
   */
  @NotNull
  public String getFontFamily() {
    return myEffectiveFontFamilies.isEmpty() ? DEFAULT_FONT_NAME : myEffectiveFontFamilies.get(0);
  }

  public void addFontFamily(@NotNull String fontFamily) {
    String fallbackFontFamily = getFallbackName(fontFamily, DEFAULT_FONT_SIZE, null);
    if (!myRealFontFamilies.contains(fontFamily)) {
      myRealFontFamilies.add(fontFamily);
    }
    String effectiveFontFamily = fallbackFontFamily == null ? fontFamily : fallbackFontFamily;
    if (!myEffectiveFontFamilies.contains(effectiveFontFamily)) {
      myEffectiveFontFamilies.add(effectiveFontFamily);
    }
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  public void copyTo(@NotNull final FontPreferences preferences) {
    preferences.myEffectiveFontFamilies.clear();
    preferences.myEffectiveFontFamilies.addAll(myEffectiveFontFamilies);
    preferences.myRealFontFamilies.clear();
    preferences.myRealFontFamilies.addAll(myRealFontFamilies);
    preferences.myFontSizes.clear();
    preferences.myTemplateFontSize = myTemplateFontSize;
    for (String fontFamily : myRealFontFamilies) {
      if (myFontSizes.containsKey(fontFamily)) {
        preferences.myFontSizes.put(fontFamily, myFontSizes.get(fontFamily));
      }
    }
    preferences.myUseLigatures = myUseLigatures;
  }

  @Override
  public int hashCode() {
    return myRealFontFamilies.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FontPreferences that = (FontPreferences)o;

    if (!myRealFontFamilies.equals(that.myRealFontFamilies)) return false;
    for (String fontFamily : myRealFontFamilies) {
      if (myFontSizes.get(fontFamily) != that.myFontSizes.get(fontFamily)) {
        return false;
      }
    }
    
    if (myUseLigatures != that.myUseLigatures) return false;

    return true;
  }

  @NotNull
  private static String getDefaultFontName() {
    if (SystemInfo.isMacOSSnowLeopard) return "Menlo";
    if (SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless()) {
      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
        if ("DejaVu Sans Mono".equals(font.getName())) {
          return font.getFontName();
        }
      }
    }
    return "Monospaced";
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
   *                        <code>null</code> if font family with the given name is registered at the current environment
   */
  @Nullable
  public static String getFallbackName(@NotNull String fontName, int fontSize, @Nullable EditorColorsScheme fallbackScheme) {
    Font plainFont = new Font(fontName, Font.PLAIN, fontSize);
    if (plainFont.getFamily().equals("Dialog") && !("Dialog".equals(fontName) || fontName.startsWith("Dialog."))) {
      return fallbackScheme == null ? DEFAULT_FONT_NAME : fallbackScheme.getEditorFontName();
    }
    return null;
  }
  
  public boolean useLigatures() {
    return myUseLigatures;
  }
  
  public void setUseLigatures(boolean useLigatures) {
    if (useLigatures != myUseLigatures) {
      myUseLigatures = useLigatures;
      if (myChangeListener != null) {
        myChangeListener.run();
      }
    }
  }

  @Override
  public String toString() {
    return "Effective font families: " + myEffectiveFontFamilies;
  }
}
