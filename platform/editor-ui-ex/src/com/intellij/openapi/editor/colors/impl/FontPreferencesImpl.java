// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.util.NlsSafe;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class which holds collection of font families and theirs sizes.
 * <p/>
 * The basic idea is to allow end-user to configure not a single font but fonts list instead - every time particular font is unable
 * to display particular char, next font is tried. This is an improvement over an old approach when it was possible to configure
 * only a single font family. Fallback fonts were chosen randomly when that font family was unable to display particular char then.
 *
 * @author Denis Zhdanov
 */
public class FontPreferencesImpl extends ModifiableFontPreferences {
  @NotNull private final Object2FloatMap<String> myFontSizes = new Object2FloatOpenHashMap<>();
  @NotNull private final List<String> myEffectiveFontFamilies = new ArrayList<>();
  @NotNull private final List<String> myRealFontFamilies = new ArrayList<>();
  @Nullable private String myRegularSubFamily;
  @Nullable private String myBoldSubFamily;

  private boolean myUseLigatures;
  private float myLineSpacing = DEFAULT_LINE_SPACING;

  @Nullable private Runnable myChangeListener;

  /**
   * Font size to use by default. Default value is {@link #DEFAULT_FONT_SIZE}.
   */
  private float myTemplateFontSize = DEFAULT_FONT_SIZE;

  public void setChangeListener(@Nullable Runnable changeListener) {
    myChangeListener = changeListener;
  }

  @Nullable
  public Runnable getChangeListener() {
    return myChangeListener;
  }

  @Override
  public void clear() {
    myFontSizes.clear();
    clearFonts();
  }

  @Override
  public void clearFonts() {
    myEffectiveFontFamilies.clear();
    myRealFontFamilies.clear();
    myUseLigatures = false;
    myRegularSubFamily = null;
    myBoldSubFamily = null;
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  @Override
  public boolean hasSize(@NotNull String fontName) {
    return myFontSizes.containsKey(fontName);
  }

  @Override
  public float getLineSpacing() {
    return myLineSpacing;
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    myLineSpacing = EditorFontsConstants.checkAndFixEditorLineSpacing(lineSpacing);
  }

  @Override
  public int getSize(@NotNull String fontFamily) {
    return (int)(getSize2D(fontFamily) + 0.5);
  }

  @Override
  public float getSize2D(@NotNull String fontFamily) {
    float result = myFontSizes.getFloat(fontFamily);
    if (result <= 0) {
      result = myTemplateFontSize;
    }
    return result > 0 ? result : DEFAULT_FONT_SIZE;
  }

  public void setSize(@NotNull String fontFamily, int size) {
    setSize(fontFamily, (float)size);
  }

  public void setSize(@NotNull String fontFamily, float size) {
    myFontSizes.put(fontFamily, size);
    myTemplateFontSize = size;
    if (myChangeListener != null) {
      myChangeListener.run();
    }
  }

  /**
   * This method might return results different from {@link #getRealFontFamilies()} when
   * {@link #getFallbackName(String, float, EditorColorsScheme) a font family unavailable at current environment}
   * has been {@link #register(String, float) registered} at the current font preferences object.
   * <p/>
   * Effective fonts will hold fallback values for such font families then (exposed by the current method), 'real fonts' will
   * be available via {@link #getRealFontFamilies()}.
   *
   * @return    effective font families to use
   */
  @Override
  @NotNull
  public List<@NlsSafe String> getEffectiveFontFamilies() {
    return myEffectiveFontFamilies;
  }

  /**
   * @return    'real' font families
   * @see #getEffectiveFontFamilies()
   */
  @Override
  @NotNull
  public List<@NlsSafe String> getRealFontFamilies() {
    return myRealFontFamilies;
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, int size) {
    register(fontFamily, (float)size);
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, float size) {
    String fallbackFontFamily = AppEditorFontOptions.NEW_FONT_SELECTOR ? null : FontPreferences.getFallbackName(fontFamily, null);
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
  @Override
  @NotNull
  @NlsSafe
  public String getFontFamily() {
    return myEffectiveFontFamilies.isEmpty() ? DEFAULT_FONT_NAME : myEffectiveFontFamilies.get(0);
  }

  @Override
  public void addFontFamily(@NotNull String fontFamily) {
    String fallbackFontFamily = AppEditorFontOptions.NEW_FONT_SELECTOR
                                ? null : FontPreferences.getFallbackName(fontFamily, null);
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

  @Override
  public void copyTo(@NotNull final FontPreferences preferences) {
    if (preferences instanceof ModifiableFontPreferences) {
      ModifiableFontPreferences modifiablePreferences = (ModifiableFontPreferences)preferences;
      modifiablePreferences.setEffectiveFontFamilies(myEffectiveFontFamilies);
      modifiablePreferences.setRealFontFamilies(myRealFontFamilies);
      modifiablePreferences.setTemplateFontSize(myTemplateFontSize);
      modifiablePreferences.resetFontSizes();
      for (String fontFamily : myRealFontFamilies) {
        if (myFontSizes.containsKey(fontFamily)) {
          modifiablePreferences.setFontSize(fontFamily, myFontSizes.getFloat(fontFamily));
        }
      }
      modifiablePreferences.setUseLigatures(myUseLigatures);
      modifiablePreferences.setLineSpacing(myLineSpacing);
      modifiablePreferences.setRegularSubFamily(myRegularSubFamily);
      modifiablePreferences.setBoldSubFamily(myBoldSubFamily);
    }
  }

  @Override
  public void resetFontSizes() {
    myFontSizes.clear();
  }

  @Override
  public void setFontSize(@NotNull String fontFamily, int size) {
    setFontSize(fontFamily, (float)size);
  }

  @Override
  public void setFontSize(@NotNull String fontFamily, float size) {
    myFontSizes.put(fontFamily, size);
  }

  @Override
  public void setTemplateFontSize(int size) {
    setTemplateFontSize((float)size);
  }

  @Override
  public void setTemplateFontSize(float size) {
    myTemplateFontSize = size;
  }

  @Override
  public void setEffectiveFontFamilies(@NotNull List<String> fontFamilies) {
    myEffectiveFontFamilies.clear();
    myEffectiveFontFamilies.addAll(fontFamilies);
  }

  @Override
  public void setRealFontFamilies(@NotNull List<String> fontFamilies) {
    myRealFontFamilies.clear();
    myRealFontFamilies.addAll(fontFamilies);
  }

  @Override
  public int hashCode() {
    return myRealFontFamilies.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FontPreferencesImpl that = (FontPreferencesImpl)o;

    if (!myRealFontFamilies.equals(that.myRealFontFamilies)) return false;
    for (String fontFamily : myRealFontFamilies) {
      if (myFontSizes.getFloat(fontFamily) != that.myFontSizes.getFloat(fontFamily)) {
        return false;
      }
    }

    if (myUseLigatures != that.myUseLigatures) return false;
    if (myLineSpacing != that.myLineSpacing) return false;
    if (!Objects.equals(myRegularSubFamily, that.myRegularSubFamily)) return false;
    if (!Objects.equals(myBoldSubFamily, that.myBoldSubFamily)) return false;

    return true;
  }

  @Override
  public boolean useLigatures() {
    return myUseLigatures;
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    if (useLigatures != myUseLigatures) {
      myUseLigatures = useLigatures;
      if (myChangeListener != null) {
        myChangeListener.run();
      }
    }
  }

  @Override
  public @Nullable String getRegularSubFamily() {
    return myRegularSubFamily;
  }

  @Override
  public @Nullable String getBoldSubFamily() {
    return myBoldSubFamily;
  }

  @Override
  public void setRegularSubFamily(String subFamily) {
    if (!Objects.equals(myRegularSubFamily, subFamily)) {
      myRegularSubFamily = subFamily;
      if (myChangeListener != null) {
        myChangeListener.run();
      }
    }
  }

  @Override
  public void setBoldSubFamily(String subFamily) {
    if (!Objects.equals(myBoldSubFamily, subFamily)) {
      myBoldSubFamily = subFamily;
      if (myChangeListener != null) {
        myChangeListener.run();
      }
    }
  }

  @Override
  @NonNls
  public String toString() {
    return "Effective font families: " + myEffectiveFontFamilies;
  }
}
