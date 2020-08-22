// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "DefaultFont", storages = @Storage("editor.xml"))
public class AppEditorFontOptions implements PersistentStateComponent<AppEditorFontOptions.PersistentFontPreferences> {

  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();

  public AppEditorFontOptions() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment() || ApplicationManager.getApplication().isUnitTestMode()) {
      myFontPreferences.register(
        FontPreferences.DEFAULT_FONT_NAME,
        UISettings.restoreFontSize(FontPreferences.DEFAULT_FONT_SIZE, 1.0f));
    }
  }

  public static class PersistentFontPreferences {
    public int FONT_SIZE = FontPreferences.DEFAULT_FONT_SIZE;
    public @NotNull String FONT_FAMILY = FontPreferences.DEFAULT_FONT_NAME;
    public float FONT_SCALE = 1.0f;
    public float LINE_SPACING = FontPreferences.DEFAULT_LINE_SPACING;
    public boolean USE_LIGATURES = false;
    public @Nullable String SECONDARY_FONT_FAMILY;

    /**
     * Serialization constructor.
     */
    private PersistentFontPreferences() {
    }

    public PersistentFontPreferences(FontPreferences fontPreferences) {
      FONT_FAMILY = fontPreferences.getFontFamily();
      FONT_SIZE = fontPreferences.getSize(FONT_FAMILY);
      FONT_SCALE = UISettings.getDefFontScale();
      LINE_SPACING = fontPreferences.getLineSpacing();
      USE_LIGATURES = fontPreferences.useLigatures();
      List<String> fontFamilies = fontPreferences.getEffectiveFontFamilies();
      if (fontFamilies.size() > 1) {
        SECONDARY_FONT_FAMILY = fontFamilies.get(1);
      }
    }

    private static PersistentFontPreferences getDefaultState() {
      return new PersistentFontPreferences();
    }
  }


  public static AppEditorFontOptions getInstance() {
    return ServiceManager.getService(AppEditorFontOptions.class);
  }

  @Nullable
  @Override
  public PersistentFontPreferences getState() {
    return new PersistentFontPreferences(myFontPreferences);
  }

  @Override
  public void loadState(@NotNull PersistentFontPreferences state) {
    copyState(state, myFontPreferences);
    myFontPreferences.setChangeListener(() -> EditorFontCache.getInstance().reset());
  }

  private static void copyState(PersistentFontPreferences state, @NotNull ModifiableFontPreferences fontPreferences) {
    fontPreferences.clear();
    int fontSize = UISettings.restoreFontSize(state.FONT_SIZE, state.FONT_SCALE);
    fontPreferences.register(state.FONT_FAMILY, fontSize);
    fontPreferences.setLineSpacing(state.LINE_SPACING);
    fontPreferences.setUseLigatures(state.USE_LIGATURES);
    if (state.SECONDARY_FONT_FAMILY != null) {
      fontPreferences.register(state.SECONDARY_FONT_FAMILY, fontSize);
    }
  }

  public static void initDefaults(@NotNull ModifiableFontPreferences fontPreferences) {
    copyState(PersistentFontPreferences.getDefaultState(), fontPreferences);
  }

  @NotNull
  public FontPreferences getFontPreferences() {
    return myFontPreferences;
  }
}
