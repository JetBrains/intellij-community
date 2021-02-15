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

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.FontEditorPreview;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AppEditorFontPanel implements Disposable {

  @NotNull private final AppEditorFontOptionsPanel myOptionsPanel;
  @NotNull private final FontEditorPreview myPreview;
  @NotNull private final EditorColorsScheme myPreviewScheme;
  @NotNull private final JPanel myTopPanel;
  @NotNull private final JLabel myRestoreLabel;

  public AppEditorFontPanel() {
    myTopPanel = new JPanel(new BorderLayout());
    JPanel restorePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    myRestoreLabel = createRestoreLabel();
    restorePanel.add(myRestoreLabel);
    myTopPanel.add(restorePanel, BorderLayout.NORTH);
    JBSplitter splitter = new JBSplitter(false, 0.3f);
    myPreviewScheme = createPreviewScheme();
    myOptionsPanel = new AppEditorFontOptionsPanel(this, myPreviewScheme);
    myPreview = new FontEditorPreview(()-> myPreviewScheme, true);
    splitter.setFirstComponent(myOptionsPanel);
    splitter.setSecondComponent(myPreview.getPanel());
    myTopPanel.add(splitter, BorderLayout.CENTER);
    myOptionsPanel.addListener(
      new ColorAndFontSettingsListener.Abstract() {
        @Override
        public void fontChanged() {
          updatePreview();
        }
      }
    );
  }

  void setRestoreLabelEnabled(boolean isEnabled) {
    myRestoreLabel.setEnabled(isEnabled);
  }

  @NotNull
  private JLabel createRestoreLabel() {
    return new LinkLabel<>(ApplicationBundle.message("settings.editor.font.restored.defaults"), null, new LinkListener<>() {
      @Override
      public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
        myOptionsPanel.restoreDefaults();
      }
    });
  }

  public void updatePreview() {
    if (myPreviewScheme instanceof EditorFontCache) {
      ((EditorFontCache)myPreviewScheme).reset();
    }
    myPreview.updateView();
  }

  @Override
  public void dispose() {
    myPreview.disposeUIResources();
  }

  @NotNull
  private static EditorColorsScheme createPreviewScheme() {
    EditorColorsScheme scheme = (EditorColorsScheme)EditorColorsManager.getInstance().getSchemeForCurrentUITheme().clone();
    scheme.setFontPreferences(new FontPreferencesImpl());
    return scheme;
  }

  @NotNull
  public JPanel getComponent() {
    return myTopPanel;
  }

  @NotNull
  public AppEditorFontOptionsPanel getOptionsPanel() {
    return myOptionsPanel;
  }
}
