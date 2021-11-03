// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@State(name = "DefaultFont", storages = {
  @Storage(value = "editor-font.xml", roamingType = RoamingType.DISABLED),
  @Storage(value = "editor.xml", deprecated = true)
}, category = SettingsCategory.UI)
public final class AppEditorFontOptions implements
                                        PersistentStateComponentWithModificationTracker<AppEditorFontOptions.PersistentFontPreferences> {
  private static final Logger LOG = Logger.getInstance(AppEditorFontOptions.class);
  public static final boolean NEW_FONT_SELECTOR = SystemProperties.getBooleanProperty("new.editor.font.selector", true);

  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  private static final int CURR_FONT_PREF_VERSION = 1;
  private              int myFontPrefVersion;

  public AppEditorFontOptions() {
    Application app = ApplicationManager.getApplication();
    if (!app.isHeadlessEnvironment() || app.isUnitTestMode()) {
      myFontPreferences.register(FontPreferences.DEFAULT_FONT_NAME, UISettings.restoreFontSize(FontPreferences.DEFAULT_FONT_SIZE, 1.0f));
    }
  }

  @Override
  public long getStateModificationCount() {
    if (myFontPrefVersion < CURR_FONT_PREF_VERSION) {
      myTracker.incModificationCount();
    }
    return myTracker.getModificationCount();
  }

  public static class PersistentFontPreferences {
    public int VERSION = 0;

    @ReportValue
    public int FONT_SIZE = FontPreferences.DEFAULT_FONT_SIZE;
    @ReportValue
    public @NlsSafe @NotNull String FONT_FAMILY = FontPreferences.DEFAULT_FONT_NAME;
    @ReportValue
    public @NlsSafe @Nullable String FONT_REGULAR_SUB_FAMILY;
    @ReportValue
    public @NlsSafe @Nullable String FONT_BOLD_SUB_FAMILY;
    @ReportValue
    public float FONT_SCALE = 1.0f;
    @ReportValue
    public float LINE_SPACING = FontPreferences.DEFAULT_LINE_SPACING;
    @ReportValue
    public boolean USE_LIGATURES = false;
    @ReportValue
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
      PersistentFontPreferences preferences = new PersistentFontPreferences();
      preferences.VERSION = CURR_FONT_PREF_VERSION;
      return preferences;
    }
  }


  public static AppEditorFontOptions getInstance() {
    return ApplicationManager.getApplication().getService(AppEditorFontOptions.class);
  }

  @Override
  public @NotNull PersistentFontPreferences getState() {
    PersistentFontPreferences preferences = new PersistentFontPreferences(myFontPreferences);
    preferences.VERSION = CURR_FONT_PREF_VERSION;
    return preferences;
  }

  @Override
  public void loadState(@NotNull PersistentFontPreferences state) {
    copyState(state, myFontPreferences);
    myFontPrefVersion = state.VERSION;
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

  public void update(@NotNull FontPreferences newPreferences) {
    newPreferences.copyTo(myFontPreferences);
    myTracker.incModificationCount();
  }

  public @NotNull FontPreferences getFontPreferences() {
    return myFontPreferences;
  }
}
