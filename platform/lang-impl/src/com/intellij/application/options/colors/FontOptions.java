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

package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FontOptions extends AbstractFontOptionsPanel {

  @NotNull private final ColorAndFontOptions myOptions;

  private @Nullable JCheckBox myInheritFontCheckbox;
  
  public FontOptions(@NotNull ColorAndFontOptions options) {
    myOptions = options;
  }

  @Nullable
  protected String getInheritFontTitle() {
    return "Use default font preferences";
  }

  @Override
  protected void initControls() {
    myInheritFontCheckbox = getInheritFontTitle() != null ? new JCheckBox(getInheritFontTitle()) : null;
    if (myInheritFontCheckbox != null) {
      add(myInheritFontCheckbox, "newline, sx 2");
      myInheritFontCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setDelegatingPreferences(myInheritFontCheckbox.isSelected());
        }
      });
      add(new JSeparator(), "newline, growx, span");
    }
    super.initControls();
  }

  protected void setDelegatingPreferences(boolean isDelegating) {
    FontPreferences currPrefs = getCurrentScheme().getFontPreferences();
    if (currPrefs instanceof DelegatingFontPreferences == isDelegating) return;
    if (isDelegating) {
      getCurrentScheme().setUseAppFontPreferencesInEditor();
    }
    else {
      getCurrentScheme().setFontPreferences(getFontPreferences());
    }
    updateOptionsList();
    updateDescription(true);
  }

  @Override
  protected boolean isReadOnly() {
    return ColorAndFontOptions.isReadOnly(myOptions.getSelectedScheme());
  }

  @Override
  protected boolean isDelegating() {
    return getFontPreferences() instanceof DelegatingFontPreferences;
  }

  @NotNull
  @Override
  protected FontPreferences getFontPreferences() {
    return getCurrentScheme().getFontPreferences();
  }

  @Override
  protected void setFontSize(int fontSize) {
    getCurrentScheme().setEditorFontSize(fontSize);
  }

  @Override
  protected float getLineSpacing() {
    return getCurrentScheme().getLineSpacing();
  }

  @Override
  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setLineSpacing(lineSpacing);
  }

  protected EditorColorsScheme getCurrentScheme() {
    return myOptions.getSelectedScheme();
  }

  @Override
  protected void updateCustomOptions() {
    if (myInheritFontCheckbox != null) {
      myInheritFontCheckbox.setEnabled(!isReadOnly());
      myInheritFontCheckbox.setSelected(isDelegating());
    }
  }

}
