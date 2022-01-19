// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public abstract class AppFontOptions<F extends PersistentFontPreferences>
  implements PersistentStateComponentWithModificationTracker<F> {

  private static final Logger LOG = Logger.getInstance(AppFontOptions.class);

  @ApiStatus.Internal
  public static final boolean NEW_FONT_SELECTOR = SystemProperties.getBooleanProperty("new.editor.font.selector", true);
  @ApiStatus.Internal
  public static final boolean APP_CONSOLE_FONT_ENABLED = SystemProperties.getBooleanProperty("app.console.font.enabled", false);

  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();
  protected final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  protected static final int CURR_FONT_PREF_VERSION = 1;
  private int myFontPrefVersion;

  public AppFontOptions() {
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


  @Override
  public @NotNull F getState() {
    F preferences = createFontState(myFontPreferences);
    preferences.VERSION = CURR_FONT_PREF_VERSION;
    return preferences;
  }

  @Override
  public void loadState(@NotNull F state) {
    copyState(state, myFontPreferences);
    myFontPrefVersion = state.VERSION;
    myFontPreferences.setChangeListener(() -> EditorFontCache.getInstance().reset());
  }

  protected abstract F createFontState(@NotNull FontPreferences fontPreferences);

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
    return new String[]{family, regularSubFamily, boldSubFamily};
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