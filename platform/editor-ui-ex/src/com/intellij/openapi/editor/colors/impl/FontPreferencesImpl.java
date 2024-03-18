// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class which holds collection of font families and theirs sizes.
 * <p/>
 * The basic idea is to allow end-user to configure not a single font but fonts list instead - every time particular font is unable
 * to display particular char, next font is tried. This is an improvement over an old approach when it was possible to configure
 * only a single font family. Fallback fonts were chosen randomly when that font family was unable to display particular char then.
 */
public class FontPreferencesImpl extends ModifiableFontPreferences {
  private final @NotNull Object2FloatMap<String> myFontSizes = new Object2FloatOpenHashMap<>();
  private final @NotNull List<String> myEffectiveFontFamilies = new ArrayList<>();
  private final @NotNull List<String> myRealFontFamilies = new ArrayList<>();
  private @Nullable String myRegularSubFamily;
  private @Nullable String myBoldSubFamily;

  private boolean myUseLigatures;
  private float myLineSpacing = DEFAULT_LINE_SPACING;

  private final @NotNull EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  /**
   * Font size to use by default. Default value is {@link #DEFAULT_FONT_SIZE}.
   */
  private float myTemplateFontSize = DEFAULT_FONT_SIZE;

  private static final Logger LOG = Logger.getInstance(FontPreferencesImpl.class);

  public void addChangeListener(@NotNull ChangeListener changeListener) {
    myEventDispatcher.addListener(changeListener);
  }

  public void addChangeListener(@NotNull ChangeListener changeListener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(changeListener, parentDisposable);
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
    notifyStateChanged();
  }

  private void notifyStateChanged() {
    myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
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
    logSizeChangeIfNeeded(size);
    myFontSizes.put(fontFamily, size);
    myTemplateFontSize = size;
    notifyStateChanged();
  }

  private void logSizeChangeIfNeeded(float size) {
    if (!LOG.isDebugEnabled()) return;
    EditorColorsManager colorsManager = ApplicationManager.getApplication().getServiceIfCreated(EditorColorsManager.class);
    if (colorsManager == null || colorsManager.getGlobalScheme().getFontPreferences() != this) return;

    LOG.debug("Will set size %s to global font (presentationMode=%b)".formatted(size, UISettings.getInstance().getPresentationMode()));
    LOG.debug(ExceptionUtil.currentStackTrace());
  }

  @Override
  public @NotNull List<@NlsSafe String> getEffectiveFontFamilies() {
    return myEffectiveFontFamilies;
  }

  @Override
  public @NotNull List<@NlsSafe String> getRealFontFamilies() {
    return myRealFontFamilies;
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, int size) {
    register(fontFamily, (float)size);
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, float size) {
    if (!myRealFontFamilies.contains(fontFamily)) {
      myRealFontFamilies.add(fontFamily);
    }
    if (!myEffectiveFontFamilies.contains(fontFamily)) {
      myEffectiveFontFamilies.add(fontFamily);
    }
    setSize(fontFamily, size);
  }

  /**
   * @return first element of the {@link #getEffectiveFontFamilies() registered font families} (if any);
   *         {@link #DEFAULT_FONT_NAME} otherwise
   */
  @Override
  public @NotNull @NlsSafe String getFontFamily() {
    return myEffectiveFontFamilies.isEmpty() ? DEFAULT_FONT_NAME : myEffectiveFontFamilies.get(0);
  }

  @Override
  public void addFontFamily(@NotNull String fontFamily) {
    if (!myRealFontFamilies.contains(fontFamily)) {
      myRealFontFamilies.add(fontFamily);
    }
    if (!myEffectiveFontFamilies.contains(fontFamily)) {
      myEffectiveFontFamilies.add(fontFamily);
    }
    notifyStateChanged();
  }

  @Override
  public void copyTo(final @NotNull FontPreferences preferences) {
    if (preferences instanceof ModifiableFontPreferences modifiablePreferences) {
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
      notifyStateChanged();
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
      notifyStateChanged();
    }
  }

  @Override
  public void setBoldSubFamily(String subFamily) {
    if (!Objects.equals(myBoldSubFamily, subFamily)) {
      myBoldSubFamily = subFamily;
      notifyStateChanged();
    }
  }

  @Override
  public @NonNls String toString() {
    return "Effective font families: " + myEffectiveFontFamilies;
  }
}
