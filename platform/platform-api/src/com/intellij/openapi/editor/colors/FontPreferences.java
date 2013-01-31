/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  @NotNull private final TObjectIntHashMap<String> myFontSizes    = new TObjectIntHashMap<String>();
  @NotNull private final List<String>              myFontFamilies = ContainerUtilRt.newArrayList();

  /**
   * Font size to use by default. Default value is {@link #DEFAULT_FONT_SIZE}. 
   */
  private int myTemplateFontSize = DEFAULT_FONT_SIZE;
  
  public void clear() {
    myFontFamilies.clear();
    myFontSizes.clear();
  }

  public void clearFonts() {
    myFontFamilies.clear();
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
  }

  @NotNull
  public List<String> getFontFamilies() {
    return myFontFamilies;
  }

  public void register(@NotNull String fontFamily, int size) {
    if (!myFontFamilies.contains(fontFamily)) {
      myFontFamilies.add(fontFamily);
    }
    setSize(fontFamily, size);
  }

  /**
   * @return first element of the {@link #getFontFamilies() registered font families} (if any);
   *         {@link #DEFAULT_FONT_NAME} otherwise
   */
  @NotNull
  public String getFontFamily() {
    return myFontFamilies.isEmpty() ? DEFAULT_FONT_NAME : myFontFamilies.get(0);
  }

  public void addFontFamily(@NotNull String fontFamily) {
    if (!myFontFamilies.contains(fontFamily)) {
      myFontFamilies.add(fontFamily);
    }
  }
  
  public void copyTo(@NotNull final FontPreferences preferences) {
    preferences.myFontFamilies.clear();
    preferences.myFontFamilies.addAll(myFontFamilies);
    preferences.myFontSizes.clear();
    for (String fontFamily : myFontFamilies) {
      if (myFontSizes.containsKey(fontFamily)) {
        preferences.myFontSizes.put(fontFamily, myFontSizes.get(fontFamily));
      }
    }
  }

  @Override
  public int hashCode() {
    return myFontFamilies.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FontPreferences that = (FontPreferences)o;

    if (!myFontFamilies.equals(that.myFontFamilies)) return false;
    for (String fontFamily : myFontFamilies) {
      if (myFontSizes.get(fontFamily) != that.myFontSizes.get(fontFamily)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  private static String getDefaultFontName() {
    if (SystemInfo.isMacOSSnowLeopard) return "Menlo";
    if (SystemInfo.isXWindow) {
      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
        if ("DejaVu Sans Mono".equals(font.getName())) {
          return font.getFontName();
        }
      }
    }
    return "Monospaced";
  }

}
