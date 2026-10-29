// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions.PersistentFontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.util.SimpleModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class AppFontOptions<F extends PersistentFontPreferences>
  implements PersistentStateComponentWithModificationTracker<F> {

  private static final Logger LOG = Logger.getInstance(AppFontOptions.class);
  protected static final int CURR_FONT_PREF_VERSION = 1;

  public static void initDefaults(@NotNull ModifiableFontPreferences fontPreferences) {
    copyState(PersistentFontPreferences.getDefaultState(), fontPreferences);
  }

  private final FontPreferencesImpl myFontPreferences;
  private final SimpleModificationTracker myTracker;
  private int myFontPrefVersion;

  public AppFontOptions() {
    myFontPreferences = new FontPreferencesImpl();
    myTracker = new SimpleModificationTracker();
    Application app = ApplicationManager.getApplication();
    if (!app.isHeadlessEnvironment() || app.isUnitTestMode()) {
      myFontPreferences.register(
        FontPreferences.DEFAULT_FONT_NAME,
        UISettings.restoreFontSize((float)FontPreferences.DEFAULT_FONT_SIZE, 1.0f)
      );
      if (FontFamilyService.isServiceSupported()) {
        String regular = FontFamilyService.getRecommendedSubFamily(FontPreferences.DEFAULT_FONT_NAME);
        myFontPreferences.setRegularSubFamily(regular);
        myFontPreferences.setBoldSubFamily(FontFamilyService.getRecommendedBoldSubFamily(FontPreferences.DEFAULT_FONT_NAME, regular));
      }
    }
    myFontPreferences.addChangeListener((_) -> resetEditorFontCache());
  }

  protected abstract F createFontState(@NotNull FontPreferences fontPreferences);

  protected void incModificationCount() {
    myTracker.incModificationCount();
  }

  @Override
  public long getStateModificationCount() {
    if (myFontPrefVersion < CURR_FONT_PREF_VERSION) {
      incModificationCount();
    }
    return myTracker.getModificationCount();
  }

  @Override
  public @NotNull F getState() {
    F preferences = createFontState(myFontPreferences);
    preferences.VERSION = CURR_FONT_PREF_VERSION;
    return preferences;
  }

  @Override
  public void loadState(@NotNull F state) {
    copyState(state, myFontPreferences);
    loadVersion(state);
  }

  /**
   * Marks the preferences as current, because a profile without a state has nothing to migrate.
   */
  @Override
  public void noStateLoaded() {
    myFontPrefVersion = CURR_FONT_PREF_VERSION;
  }

  /**
   * Records the version of the loaded state.
   * An older version makes {@link #getStateModificationCount()} report a change, so the store saves the migrated state.
   */
  void loadVersion(@NotNull F state) {
    myFontPrefVersion = state.VERSION;
  }

  public void update(@NotNull FontPreferences newPreferences) {
    newPreferences.copyTo(myFontPreferences);
    incModificationCount();
  }

  public @NotNull FontPreferences getFontPreferences() {
    return myFontPreferences;
  }

  private static void copyState(@NotNull PersistentFontPreferences state, @NotNull ModifiableFontPreferences fontPreferences) {
    // Check both float and integer font size for compatibility
    float stateFontSize = state.FONT_SIZE_2D != (float)FontPreferences.DEFAULT_FONT_SIZE ? state.FONT_SIZE_2D : state.FONT_SIZE;
    float fontSize = UISettings.restoreFontSize(stateFontSize, state.FONT_SCALE);
    String[] names = migrateFamilyNameIfNeeded(state.FONT_FAMILY, state.FONT_REGULAR_SUB_FAMILY, state.FONT_BOLD_SUB_FAMILY);
    fontPreferences.runBatchUpdate(draft -> {
      draft.clear();
      draft.register(names[0], fontSize);
      draft.setRegularSubFamily(names[1]);
      draft.setBoldSubFamily(names[2]);
      draft.setLineSpacing(state.LINE_SPACING);
      draft.setUseLigatures(state.USE_LIGATURES);
      draft.setCharacterVariants(state.CHARACTER_VARIANTS);
      if (state.SECONDARY_FONT_FAMILY != null) {
        draft.register(state.SECONDARY_FONT_FAMILY, fontSize);
      }
    });
  }

  private static String @NotNull [] migrateFamilyNameIfNeeded(
    String family,
    @Nullable String regularSubFamily,
    @Nullable String boldSubFamily
  ) {
    if (regularSubFamily == null && boldSubFamily == null && FontFamilyService.isServiceSupported()) {
      String[] result = FontFamilyService.migrateFontSetting(family);
      LOG.info("Font setting migration: " + family + " -> " + Arrays.toString(result));
      return result;
    }
    return new String[]{family, regularSubFamily, boldSubFamily};
  }

  static void resetEditorFontCache() {
    EditorFontCache cache = ApplicationManager.getApplication().getServiceIfCreated(EditorFontCache.class);
    if (cache != null) {
      cache.reset();
    }
  }
}
