/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.FontPreferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "DefaultFont", storages = @Storage("editor.xml"))
public class AppEditorFontOptions implements PersistentStateComponent<AppEditorFontOptions.PersistentFontPreferences> {

  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();

  public AppEditorFontOptions() {
    myFontPreferences.register(
      FontPreferences.DEFAULT_FONT_NAME,
      UISettings.restoreFontSize(FontPreferences.DEFAULT_FONT_SIZE, 1.0f));
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
    @SuppressWarnings("unused")
    private PersistentFontPreferences() {
    }

    public PersistentFontPreferences(FontPreferences fontPreferences) {
      FONT_FAMILY = fontPreferences.getFontFamily();
      FONT_SIZE = fontPreferences.getSize(FONT_FAMILY);
      FONT_SCALE = UISettings.getNormalizingScale();
      LINE_SPACING = fontPreferences.getLineSpacing();
      USE_LIGATURES = fontPreferences.useLigatures();
      List<String> fontFamilies = fontPreferences.getEffectiveFontFamilies();
      if (fontFamilies.size() > 1) {
        SECONDARY_FONT_FAMILY = fontFamilies.get(1);
      }
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
  public void loadState(PersistentFontPreferences state) {
    myFontPreferences.clear();
    int fontSize = UISettings.restoreFontSize(state.FONT_SIZE, state.FONT_SCALE);
    myFontPreferences.register(state.FONT_FAMILY, fontSize);
    myFontPreferences.setLineSpacing(state.LINE_SPACING);
    myFontPreferences.setUseLigatures(state.USE_LIGATURES);
    if (state.SECONDARY_FONT_FAMILY != null) {
      myFontPreferences.register(state.SECONDARY_FONT_FAMILY, fontSize);
    }
    myFontPreferences.setChangeListener(() -> EditorFontCache.getInstance().reset());
  }

  @NotNull
  public FontPreferences getFontPreferences() {
    return myFontPreferences;
  }
}
