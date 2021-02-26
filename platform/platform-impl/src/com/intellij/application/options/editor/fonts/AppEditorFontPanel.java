// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts;

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.FontEditorPreview;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class AppEditorFontPanel implements Disposable {

  @NotNull private final AppEditorFontOptionsPanel myOptionsPanel;
  @NotNull private final FontEditorPreview myPreview;
  @NotNull private final EditorColorsScheme myPreviewScheme;
  @NotNull private final JPanel myTopPanel;

  public AppEditorFontPanel() {
    myTopPanel = new JPanel(new BorderLayout());
    JPanel restorePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    myTopPanel.add(restorePanel, BorderLayout.NORTH);

    JPanel innerPanel = new JPanel(new BorderLayout());
    innerPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0,0,0));
    JBSplitter splitter = new JBSplitter(false, 0.3f);
    myPreviewScheme = createPreviewScheme();
    myOptionsPanel = new AppEditorFontOptionsPanel(myPreviewScheme);
    myOptionsPanel.setBorder(JBUI.Borders.emptyLeft(5));
    myPreview = new FontEditorPreview(()-> myPreviewScheme, true) {
      @Override
      protected Border getBorder() {
        return JBUI.Borders.customLine(JBColor.border(), 0, 1, 0,1);
      }
    };
    splitter.setFirstComponent(myOptionsPanel);
    splitter.setSecondComponent(myPreview.getPanel());
    innerPanel.add(splitter, BorderLayout.CENTER);
    myOptionsPanel.addListener(
      new ColorAndFontSettingsListener.Abstract() {
        @Override
        public void fontChanged() {
          updatePreview();
        }
      }
    );
    myTopPanel.add(innerPanel, BorderLayout.CENTER);
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
