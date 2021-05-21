// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@State(name = "DefaultFont", storages = @Storage("editor.xml"))
public final class AppEditorFontOptions implements PersistentStateComponent<AppEditorFontOptions.PersistentFontPreferences> {
  private static final Logger LOG = Logger.getInstance(AppEditorFontOptions.class);
  public static final boolean NEW_FONT_SELECTOR = SystemProperties.getBooleanProperty("new.editor.font.selector", true);

  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();

  public AppEditorFontOptions() {
    Application app = ApplicationManager.getApplication();
    if (!app.isHeadlessEnvironment() || app.isUnitTestMode()) {
      myFontPreferences.register(FontPreferences.DEFAULT_FONT_NAME, UISettings.restoreFontSize(FontPreferences.DEFAULT_FONT_SIZE, 1.0f));
    }
  }

  public static class PersistentFontPreferences {
    public int FONT_SIZE = FontPreferences.DEFAULT_FONT_SIZE;
    public @NlsSafe @NotNull String FONT_FAMILY = FontPreferences.DEFAULT_FONT_NAME;
    public @NlsSafe @Nullable String FONT_REGULAR_SUB_FAMILY;
    public @NlsSafe @Nullable String FONT_BOLD_SUB_FAMILY;
    public float FONT_SCALE = 1.0f;
    public float LINE_SPACING = FontPreferences.DEFAULT_LINE_SPACING;
    public boolean USE_LIGATURES = false;
    public @NlsSafe @Nullable String SECONDARY_FONT_FAMILY;

    /**
     * Serialization constructor.
     */
    private PersistentFontPreferences() {
    }

    public PersistentFontPreferences(FontPreferences fontPreferences) {
      FONT_FAMILY = fontPreferences.getFontFamily();
      FONT_REGULAR_SUB_FAMILY = fontPreferences.getRegularSubFamily();
      FONT_BOLD_SUB_FAMILY = fontPreferences.getBoldSubFamily();
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
    return ApplicationManager.getApplication().getService(AppEditorFontOptions.class);
  }

  @Override
  public @NotNull PersistentFontPreferences getState() {
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
    String[] names = migrateFamilyNameIfNeeded(state.FONT_FAMILY, state.FONT_REGULAR_SUB_FAMILY, state.FONT_BOLD_SUB_FAMILY);
    fontPreferences.register(names[0], fontSize);
    fontPreferences.setRegularSubFamily(names[1]);
    fontPreferences.setBoldSubFamily(names[2]);
    fontPreferences.setLineSpacing(state.LINE_SPACING);
    fontPreferences.setUseLigatures(state.USE_LIGATURES);
    if (state.SECONDARY_FONT_FAMILY != null) {
      fontPreferences.register(state.SECONDARY_FONT_FAMILY, fontSize);
    }
  }

  private static String[] migrateFamilyNameIfNeeded(String family, String regularSubFamily, String boldSubFamily) {
    if (regularSubFamily == null && boldSubFamily == null && FontFamilyService.isServiceSupported()) {
      String[] result = FontFamilyService.migrateFontSetting(family);
      LOG.info("Font setting migration: " + family + " -> " + Arrays.toString(result));
      return result;
    }
    return new String[] {family, regularSubFamily, boldSubFamily};
  }

  public static void initDefaults(@NotNull ModifiableFontPreferences fontPreferences) {
    copyState(PersistentFontPreferences.getDefaultState(), fontPreferences);
  }

  public @NotNull FontPreferences getFontPreferences() {
    return myFontPreferences;
  }
}
