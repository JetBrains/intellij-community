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
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;

import javax.swing.*;
import java.awt.*;

public class AppEditorFontPanel extends JPanel {

  private final AppEditorFontOptionsPanel myOptionsPanel;
  private final FontEditorPreview myPreview;
  private final EditorColorsScheme myPreviewScheme;

  public AppEditorFontPanel() {
    super(new BorderLayout(0,10));
    myPreviewScheme = createPreviewScheme();
    myOptionsPanel = new AppEditorFontOptionsPanel(myPreviewScheme);
    add(myOptionsPanel, BorderLayout.NORTH);
    myPreview = new FontEditorPreview(()-> myPreviewScheme, true);
    add(myPreview.getPanel(), BorderLayout.CENTER);
    myOptionsPanel.addListener(
      new ColorAndFontSettingsListener.Abstract() {
        @Override
        public void fontChanged() {
          myPreview.updateView();
        }
      }
    );
  }

  private static EditorColorsScheme createPreviewScheme() {
    EditorColorsScheme scheme = (EditorColorsScheme)EditorColorsManager.getInstance().getSchemeForCurrentUITheme().clone();
    scheme.setFontPreferences(new FontPreferencesImpl());
    return scheme;
  }

  public AppEditorFontOptionsPanel getOptionsPanel() {
    return myOptionsPanel;
  }
}
