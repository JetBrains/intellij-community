// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ReportValue;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "DefaultFont", storages = {
  @Storage(value = "editor-font.xml"),
  @Storage(value = "editor.xml", deprecated = true)
}, category = SettingsCategory.UI)
public final class AppEditorFontOptions extends AppFontOptions<AppEditorFontOptions.PersistentFontPreferences> {

  @Override
  protected PersistentFontPreferences createFontState(@NotNull FontPreferences fontPreferences) {
    return new PersistentFontPreferences(fontPreferences);
  }

  public static AppEditorFontOptions getInstance() {
    return ApplicationManager.getApplication().getService(AppEditorFontOptions.class);
  }

  public static class PersistentFontPreferences {
    public int VERSION = 0;

    @ReportValue
    public                    int    FONT_SIZE   = FontPreferences.DEFAULT_FONT_SIZE;
    @ReportValue
    public @NlsSafe @NotNull  String FONT_FAMILY = FontPreferences.DEFAULT_FONT_NAME;
    @ReportValue
    public @NlsSafe @Nullable String FONT_REGULAR_SUB_FAMILY;
    @ReportValue
    public @NlsSafe @Nullable String FONT_BOLD_SUB_FAMILY;
    @ReportValue
    public                    float   FONT_SCALE    = 1.0f;
    @ReportValue
    public                    float   LINE_SPACING  = FontPreferences.DEFAULT_LINE_SPACING;
    @ReportValue
    public                    boolean USE_LIGATURES = false;
    @ReportValue
    public @NlsSafe @Nullable String  SECONDARY_FONT_FAMILY;

    /**
     * Serialization constructor.
     */
    protected PersistentFontPreferences() {
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

    protected static PersistentFontPreferences getDefaultState() {
      PersistentFontPreferences preferences = new PersistentFontPreferences();
      preferences.VERSION = CURR_FONT_PREF_VERSION;
      return preferences;
    }
  }
}
