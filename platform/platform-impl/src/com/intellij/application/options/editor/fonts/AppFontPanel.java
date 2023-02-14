// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.FontEditorPreview;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontCache;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

public class AppFontPanel implements Disposable {

  @NotNull private final AppFontOptionsPanel myOptionsPanel;
  @NotNull private final FontEditorPreview   myPreview;
  @NotNull private final EditorColorsScheme  myPreviewScheme;
  @NotNull private final JPanel              myTopPanel;
  private                JLabel              myEditorFontLabel;
  @NotNull private final JPanel              myWarningPanel;

  public AppFontPanel(@NotNull FontOptionsPanelFactory fontOptionsPanelFactory) {
    myTopPanel = new JPanel(new BorderLayout());
    myWarningPanel = createMessagePanel();
    myTopPanel.add(myWarningPanel, BorderLayout.NORTH);

    JPanel innerPanel = new JPanel(new BorderLayout());
    innerPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0,0,0));
    JBSplitter splitter = new JBSplitter(false, 0.3f);
    myPreviewScheme = createPreviewScheme();
    myOptionsPanel = fontOptionsPanelFactory.create(myPreviewScheme);
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
          updateWarning();
        }
      }
    );
    myTopPanel.add(innerPanel, BorderLayout.CENTER);
  }

  private JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    messagePanel.add(new JLabel(AllIcons.General.BalloonWarning));
    myEditorFontLabel = createHyperlinkLabel();
    messagePanel.add(myEditorFontLabel);
    JLabel commentLabel = new JLabel(ApplicationBundle.message("settings.editor.font.defined.in.color.scheme.message"));
    commentLabel.setForeground(JBColor.GRAY);
    messagePanel.add(commentLabel);
    return messagePanel;
  }


  @NotNull
  private JLabel createHyperlinkLabel() {
    HoverHyperlinkLabel label = new HoverHyperlinkLabel("");
    label.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          navigateToColorSchemeFontConfigurable();
        }
      }
    });
    return label;
  }

  protected void navigateToColorSchemeFontConfigurable() {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myTopPanel));
    if (allSettings != null) {
      final Configurable colorSchemeConfigurable = allSettings.find(ColorAndFontOptions.ID);
      if (colorSchemeConfigurable instanceof ColorAndFontOptions) {
        Configurable fontOptions =
          ((ColorAndFontOptions)colorSchemeConfigurable).findSubConfigurable(ColorAndFontOptions.getFontConfigurableName());
        if (fontOptions != null) {
          allSettings.select(fontOptions);
        }
      }
    }
  }

  public void updateWarning() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    if (!scheme.isUseAppFontPreferencesInEditor()) {
      myEditorFontLabel.setText(
        ApplicationBundle.message("settings.editor.font.overridden.message", scheme.getEditorFontName(), scheme.getEditorFontSize()));
      myWarningPanel.setVisible(true);
    }
    else {
      myWarningPanel.setVisible(false);
    }
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
  public AppFontOptionsPanel getOptionsPanel() {
    return myOptionsPanel;
  }

  protected interface FontOptionsPanelFactory {
    @NotNull AppFontOptionsPanel create(@NotNull EditorColorsScheme previewScheme);
  }
}
