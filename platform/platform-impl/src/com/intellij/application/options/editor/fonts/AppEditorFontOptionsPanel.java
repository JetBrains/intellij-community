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
package com.intellij.application.options.editor.fonts;

import com.intellij.application.options.colors.AbstractFontOptionsPanel;
import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.ui.AbstractFontCombo;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AppEditorFontOptionsPanel extends AbstractFontOptionsPanel {

  private final EditorColorsScheme myScheme;
  private final FontPreferences myDefaultPreferences;
  private AppEditorTypographyOptions appEditorTypographyOptions;

  private ActionLink myRestoreLabel;

  protected AppEditorFontOptionsPanel(EditorColorsScheme scheme) {
    myScheme = scheme;
    myDefaultPreferences = new FontPreferencesImpl();
    AppEditorFontOptions.initDefaults((ModifiableFontPreferences)myDefaultPreferences);
    updateOptionsList();
  }

  @Override
  protected JComponent createControls() {
    JPanel topPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.emptyInsets();
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.WEST;
    topPanel.add(createFontSettingsPanel(), c);
    c.gridy ++;
    c.insets = JBUI.insets(ADDITIONAL_VERTICAL_GAP, BASE_INSET, 0, 0);
    myRestoreLabel = createRestoreLabel();
    topPanel.add(myRestoreLabel, c);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridy ++;
    c.insets = JBUI.insets(ADDITIONAL_VERTICAL_GAP, 0);

    appEditorTypographyOptions = new AppEditorTypographyOptions(this);
    topPanel.add(appEditorTypographyOptions.createPanel(), c);
    createSecondaryFontComboAndLabel(appEditorTypographyOptions.getSecondaryFontLabel());

    addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void fontChanged() {
        appEditorTypographyOptions.updateFontPreferences(getFontPreferences());
        updateRestoreButtonState();
      }
    });
    return topPanel;
  }

  void restoreDefaults() {
    AppEditorFontOptions.initDefaults((ModifiableFontPreferences)getFontPreferences());
    updateOnChangedFont();
  }


  @NotNull
  private ActionLink createRestoreLabel() {
    return new ActionLink(ApplicationBundle.message("settings.editor.font.restored.defaults"), e -> {
      restoreDefaults();
    });
  }

  public void updateOnChangedFont() {
    updateOptionsList();
    fireFontChanged();
  }

  private void updateRestoreButtonState() {
    myRestoreLabel.setEnabled(!myDefaultPreferences.equals(getFontPreferences()));
  }

  @Override
  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected boolean isDelegating() {
    return false;
  }

  @NotNull
  @Override
  protected FontPreferences getFontPreferences() {
    return myScheme.getFontPreferences();
  }

  @Override
  protected void setFontSize(int fontSize) {
    myScheme.setEditorFontSize(fontSize);
  }

  @Override
  protected float getLineSpacing() {
    return myScheme.getLineSpacing();
  }

  @Override
  protected void setCurrentLineSpacing(float lineSpacing) {
    myScheme.setLineSpacing(lineSpacing);
  }

  @Override
  protected AbstractFontCombo<?> createPrimaryFontCombo() {
    if (AppEditorTypographyOptionsKt.isAdvancedFontFamiliesUI()) {
      return new FontFamilyCombo(true);
    }
    else {
      return super.createPrimaryFontCombo();
    }
  }

  @Override
  protected AbstractFontCombo<?> createSecondaryFontCombo() {
    if (AppEditorTypographyOptionsKt.isAdvancedFontFamiliesUI()) {
      return new FontFamilyCombo(false);
    }
    else {
      return super.createSecondaryFontCombo();
    }
  }
}
