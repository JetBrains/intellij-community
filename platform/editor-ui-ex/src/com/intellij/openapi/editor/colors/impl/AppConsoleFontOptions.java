// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.FontPreferences;
import org.jetbrains.annotations.NotNull;

@State(
  name = "ConsoleFont",
  storages = @Storage(value = "console-font.xml", roamingType = RoamingType.DISABLED),
  category = SettingsCategory.UI)
public final class AppConsoleFontOptions extends AppFontOptions<AppConsoleFontOptions.ConsoleFontState> {

  private FontPreferences myDelegatingPreferences;

  public AppConsoleFontOptions() {
    myDelegatingPreferences = new DelegatingFontPreferences(()->AppEditorFontOptions.getInstance().getFontPreferences());
  }

  public static AppConsoleFontOptions getInstance() {
    return ApplicationManager.getApplication().getService(AppConsoleFontOptions.class);
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    return myDelegatingPreferences != null ? myDelegatingPreferences : super.getFontPreferences();
  }

  @Override
  protected ConsoleFontState createFontState(@NotNull FontPreferences fontPreferences) {
    if (myDelegatingPreferences != null) {
      return new ConsoleFontState();
    }
    return new ConsoleFontState(fontPreferences);
  }

  @Override
  public void loadState(@NotNull ConsoleFontState state) {
    if (state.USE_EDITOR_FONT) {
      setUseEditorFont(true);
    }
    else {
      setUseEditorFont(false);
      super.loadState(state);
    }
  }

  public void setUseEditorFont(boolean useEditorFont) {
    if (useEditorFont) {
      myDelegatingPreferences = new DelegatingFontPreferences(()->AppEditorFontOptions.getInstance().getFontPreferences());
      myTracker.incModificationCount();
    }
    else {
      myDelegatingPreferences = null;
      update(AppEditorFontOptions.getInstance().getFontPreferences());
    }
  }

  public boolean isUseEditorFont() {
    return myDelegatingPreferences != null;
  }

  static class ConsoleFontState extends AppEditorFontOptions.PersistentFontPreferences {
    public boolean USE_EDITOR_FONT = true;

    ConsoleFontState() {
    }

    ConsoleFontState(FontPreferences fontPreferences) {
      super(fontPreferences);
      USE_EDITOR_FONT = false;
    }
  }
}
