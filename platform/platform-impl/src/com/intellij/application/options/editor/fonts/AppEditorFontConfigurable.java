// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts;

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.AppFontOptions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AppEditorFontConfigurable extends AppFontConfigurable {
  public static final @NonNls String ID = "editor.preferences.fonts.default";
  
  private AppEditorFontOptionsPanel myPanel;

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @Nls String getDisplayName() {
    return IdeBundle.message("configurable.font.name");
  }

  @Override
  protected @NotNull AppFontOptions<?> getFontOptions() {
    return AppEditorFontOptions.getInstance();
  }

  @Override
  protected @NotNull AppFontOptionsPanel createFontOptionsPanel(@NotNull EditorColorsScheme previewScheme) {
    myPanel = new AppEditorFontOptionsPanel(previewScheme);
    myPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void fontChanged() {
        AppConsoleFontConfigurable consoleConfigurable =
          (AppConsoleFontConfigurable)findConfigurable(myPanel, AppConsoleFontConfigurable.class);
        if (consoleConfigurable != null) {
          consoleConfigurable.updateOnEditorFontChange(myPanel.getFontPreferences());
        }
      }
    });
    return myPanel;
  }

  @Nullable AppEditorFontOptionsPanel getPanel() {
    return myPanel;
  }
}
