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

import com.intellij.application.options.editor.fonts.AppEditorFontConfigurable;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.colors.DelegatingFontPreferences;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.HoverHyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
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
    return "default font";
  }

  @Override
  protected void initControls() {
    createInheritCheckBox();
    super.initControls();
  }

  private void createInheritCheckBox() {
    if (getInheritFontTitle() != null) {
      JPanel inheritPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0,0 ));
      inheritPanel.setBorder(BorderFactory.createEmptyBorder());
      myInheritFontCheckbox = new JCheckBox();
      myInheritFontCheckbox.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
      myInheritFontCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setDelegatingPreferences(myInheritFontCheckbox.isSelected());
        }
      });
      inheritPanel.add(myInheritFontCheckbox);
      inheritPanel.add(new JLabel("Use "));
      inheritPanel.add(createHyperlinkLabel());

      add(inheritPanel, "newline, span");
      add(new JSeparator(), "newline, growx, span");
    }
  }

  @NotNull
  private JLabel createHyperlinkLabel() {
    HoverHyperlinkLabel label = new HoverHyperlinkLabel(getInheritFontTitle());
    label.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          navigateToParentFontConfigurable();
        }
      }
    });
    return label;
  }

  protected void navigateToParentFontConfigurable() {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(getPanel()));
    if (allSettings != null) {
      final Configurable fontConfigurable = allSettings.find(AppEditorFontConfigurable.ID);
      if (fontConfigurable != null) {
        allSettings.select(fontConfigurable);
      }
    }
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
