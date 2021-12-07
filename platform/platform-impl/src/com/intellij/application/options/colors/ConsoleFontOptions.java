/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.application.options.editor.fonts.AppConsoleFontConfigurable;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.AppConsoleFontOptions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ConsoleFontOptions extends FontOptions {
  public ConsoleFontOptions(ColorAndFontOptions options) {
    super(options);
  }

  @Override
  protected String getOverwriteFontTitle() {
    return ApplicationBundle.message("settings.editor.console.font.overwrite");
  }

  @Override
  protected @Nullable Component createOverwriteCheckBox() {
    if (!AppConsoleFontOptions.getInstance().isUseEditorFont()) {
      JPanel overwritePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      overwritePanel.setBorder(JBUI.Borders.empty());
      overwritePanel.add(new JLabel(ApplicationBundle.message("settings.editor.console.font.overwritten")));
      ActionLink consoleSettingsLink = new ActionLink(
        ApplicationBundle.message("settings.editor.console.font.overwritten.link"),
        e -> {
          navigateToParentFontConfigurable();
        });
      overwritePanel.add(consoleSettingsLink);
      return overwritePanel;
    }
    return super.createOverwriteCheckBox();
  }

  @Override
  protected void navigateToParentFontConfigurable() {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(getPanel()));
    if (allSettings != null) {
      if (!AppConsoleFontOptions.getInstance().isUseEditorFont()) {
        final Configurable fontConfigurable = allSettings.find(AppConsoleFontConfigurable.ID);
        if (fontConfigurable != null) {
          allSettings.select(fontConfigurable);
        }
      }
      else {
        ColorAndFontOptions colorAndFontOptions = allSettings.find(ColorAndFontOptions.class);
        if (colorAndFontOptions != null) {
          Configurable editorFontConfigurable = colorAndFontOptions.findSubConfigurable(ColorAndFontOptions.getFontConfigurableName());
          if (editorFontConfigurable != null) {
            allSettings.select(editorFontConfigurable);
          }
        }
      }
    }
  }

  @NotNull
  @Override
  protected FontPreferences getFontPreferences() {
    return getCurrentScheme().getConsoleFontPreferences();
  }

  @Override
  protected FontPreferences getBaseFontPreferences() {
    return getCurrentScheme().getFontPreferences();
  }

  @Override
  protected void setDelegatingPreferences(boolean isDelegating) {
    FontPreferences currPrefs = getCurrentScheme().getConsoleFontPreferences();
    if (currPrefs instanceof DelegatingFontPreferences == isDelegating) return;
    if (isDelegating) {
      getCurrentScheme().setUseEditorFontPreferencesInConsole();
    }
    else {
      getCurrentScheme().setConsoleFontPreferences(getFontPreferences());
    }
    updateOptionsList();
    updateDescription(true);
  }

  @Override
  protected void setFontSize(int fontSize) {
    getCurrentScheme().setConsoleFontSize(fontSize);
  }

  @Override
  protected float getLineSpacing() {
    return getCurrentScheme().getConsoleLineSpacing();
  }

  @Override
  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setConsoleLineSpacing(lineSpacing);
  }

  @Override
  protected boolean isReadOnly() {
    if (!AppConsoleFontOptions.getInstance().isUseEditorFont()) {
      return true;
    }
    return super.isReadOnly();
  }
}
