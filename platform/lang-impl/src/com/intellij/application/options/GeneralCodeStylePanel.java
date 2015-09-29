/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightColors;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  @SuppressWarnings("UnusedDeclaration")
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.GeneralCodeStylePanel");

  private static final String SYSTEM_DEPENDANT_STRING = ApplicationBundle.message("combobox.crlf.system.dependent");
  private static final String UNIX_STRING = ApplicationBundle.message("combobox.crlf.unix");
  private static final String WINDOWS_STRING = ApplicationBundle.message("combobox.crlf.windows");
  private static final String MACINTOSH_STRING = ApplicationBundle.message("combobox.crlf.mac");
  private final List<GeneralCodeStyleOptionsProvider> myAdditionalOptions;

  private JTextField myRightMarginField;
  private int myDefaultRightMargin;
  private Color myInitialRightMarginFieldColor;

  private JComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private JCheckBox myCbWrapWhenTypingReachesRightMargin;
  private JCheckBox myEnableFormatterTags;
  private JTextField myFormatterOnTagField;
  private JTextField myFormatterOffTagField;
  private JCheckBox myAcceptRegularExpressionsCheckBox;
  private JPanel myMarkersPanel;
  private JBLabel myFormatterOffLabel;
  private JBLabel myFormatterOnLabel;
  private JPanel myMarkerOptionsPanel;
  private JPanel myAdditionalSettingsPanel;
  private JCheckBox myAutodetectIndentsBox;
  private JCheckBox myShowDetectedIndentNotification;
  private JPanel myDefaultOptionsPanel;
  private JPanel myIndentsDetectionPanel;
  private final JScrollPane myScrollPane;


  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    //noinspection unchecked
    myLineSeparatorCombo.addItem(SYSTEM_DEPENDANT_STRING);
    //noinspection unchecked
    myLineSeparatorCombo.addItem(UNIX_STRING);
    //noinspection unchecked
    myLineSeparatorCombo.addItem(WINDOWS_STRING);
    //noinspection unchecked
    myLineSeparatorCombo.addItem(MACINTOSH_STRING);
    addPanelToWatch(myPanel);

    myDefaultRightMargin = settings.getDefaultRightMargin();
    myRightMarginField.setHorizontalAlignment(SwingConstants.RIGHT);
    myInitialRightMarginFieldColor = myRightMarginField.getBackground();

    myEnableFormatterTags.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean tagsEnabled = myEnableFormatterTags.isSelected();
        setFormatterTagControlsEnabled(tagsEnabled);
      }
    });

    myAutodetectIndentsBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean isDetectIndent = myAutodetectIndentsBox.isSelected();
        myShowDetectedIndentNotification.setEnabled(isDetectIndent);
      }
    });

    myDefaultOptionsPanel
      .setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("settings.code.style.general.default.options")));
    myIndentsDetectionPanel
      .setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("settings.code.style.general.indents.detection")));

    myMarkersPanel.setBorder(IdeBorderFactory.createTitledBorder(
      ApplicationBundle.message("settings.code.style.general.formatter.marker.title"), true));
    myMarkerOptionsPanel.setBorder(
      IdeBorderFactory.createTitledBorder(ApplicationBundle.message("settings.code.style.general.formatter.marker.options.title"), true));
    myPanel.setBorder(JBUI.Borders.empty(10, 10));
    myScrollPane = ScrollPaneFactory.createScrollPane(null, true);
    myScrollPane.setViewport(new GradientViewport(myPanel, JBUI.insetsTop(5), true));
    myScrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(10));

    myAdditionalSettingsPanel.setLayout(new VerticalFlowLayout(true, true));
    myAdditionalSettingsPanel.removeAll();
    myAdditionalOptions = ConfigurableWrapper.createConfigurables(GeneralCodeStyleOptionsProviderEP.EP_NAME);
    for (GeneralCodeStyleOptionsProvider provider : myAdditionalOptions) {
      JComponent generalSettingsComponent = provider.createComponent();
      if (generalSettingsComponent != null) {
        myAdditionalSettingsPanel.add(generalSettingsComponent);
      }
    }
  }

  @Override
  protected void somethingChanged() {
    super.somethingChanged();
  }


  @Override
  protected int getRightMargin() {
    String text = myRightMarginField.getText();
    int rightMargin;
    myRightMarginField.setBackground(myInitialRightMarginFieldColor);
    try {
      rightMargin = Integer.parseInt(text);
      if (rightMargin < 1 || rightMargin > CodeStyleSettings.MAX_RIGHT_MARGIN) {
        rightMargin = myDefaultRightMargin;
        myRightMarginField.setBackground(LightColors.RED);
      }
    }
    catch (NumberFormatException nfe) {
      myRightMarginField.setBackground(LightColors.RED);
      rightMargin = myDefaultRightMargin;
    }
    return rightMargin;
  }

  @Override
  @NotNull
  protected FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  @Override
  protected String getPreviewText() {
    return "";
  }


  @Override
  public void apply(CodeStyleSettings settings) {
    settings.LINE_SEPARATOR = getSelectedLineSeparator();

    settings.setDefaultRightMargin(getRightMargin());
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myCbWrapWhenTypingReachesRightMargin.isSelected();

    settings.FORMATTER_TAGS_ENABLED = myEnableFormatterTags.isSelected();
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = myAcceptRegularExpressionsCheckBox.isSelected();

    settings.FORMATTER_OFF_TAG = getTagText(myFormatterOffTagField, settings.FORMATTER_OFF_TAG);
    settings.setFormatterOffPattern(compilePattern(settings, myFormatterOffTagField, settings.FORMATTER_OFF_TAG));

    settings.FORMATTER_ON_TAG = getTagText(myFormatterOnTagField, settings.FORMATTER_ON_TAG);
    settings.setFormatterOnPattern(compilePattern(settings, myFormatterOnTagField, settings.FORMATTER_ON_TAG));

    settings.AUTODETECT_INDENTS = myAutodetectIndentsBox.isSelected();
    if (myShowDetectedIndentNotification.isEnabled()) {
      FileIndentOptionsProvider.setShowNotification(myShowDetectedIndentNotification.isSelected());
    }

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.apply(settings);
    }
  }

  @Nullable
  private static Pattern compilePattern(CodeStyleSettings settings, JTextField field, String patternText) {
    try {
      return Pattern.compile(patternText);
    }
    catch (PatternSyntaxException pse) {
      settings.FORMATTER_TAGS_ACCEPT_REGEXP = false;
      showError(field, ApplicationBundle.message("settings.code.style.general.formatter.marker.invalid.regexp"));
      return null;
    }
  }

  private static String getTagText(JTextField field, String defaultValue) {
    String fieldText = field.getText();
    if (StringUtil.isEmpty(field.getText())) {
      field.setText(defaultValue);
      return defaultValue;
    }
    return fieldText;
  }

  @Nullable
  private String getSelectedLineSeparator() {
    if (UNIX_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\n";
    }
    else if (MACINTOSH_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r";
    }
    else if (WINDOWS_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r\n";
    }
    return null;
  }


  @Override
  public boolean isModified(CodeStyleSettings settings) {
    if (!Comparing.equal(getSelectedLineSeparator(), settings.LINE_SEPARATOR)) {
      return true;
    }

    if (settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN ^ myCbWrapWhenTypingReachesRightMargin.isSelected()) {
      return true;
    }

    if (getRightMargin() != settings.getDefaultRightMargin()) return true;

    if (myEnableFormatterTags.isSelected()) {
      if (
        !settings.FORMATTER_TAGS_ENABLED ||
        settings.FORMATTER_TAGS_ACCEPT_REGEXP != myAcceptRegularExpressionsCheckBox.isSelected() ||
        !StringUtil.equals(myFormatterOffTagField.getText(), settings.FORMATTER_OFF_TAG) ||
        !StringUtil.equals(myFormatterOnTagField.getText(), settings.FORMATTER_ON_TAG)) return true;
    }
    else {
      if (settings.FORMATTER_TAGS_ENABLED) return true;
    }

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      if (option.isModified(settings)) return true;
    }

    if (settings.AUTODETECT_INDENTS != myAutodetectIndentsBox.isSelected()) return true;

    if (myShowDetectedIndentNotification.isEnabled()
        && FileIndentOptionsProvider.isShowNotification() != myShowDetectedIndentNotification.isSelected())
    {
      return true;
    }

    return false;
  }

  @Override
  public JComponent getPanel() {
    return myScrollPane;
  }

  @Override
  protected void resetImpl(final CodeStyleSettings settings) {

    String lineSeparator = settings.LINE_SEPARATOR;
    if ("\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(UNIX_STRING);
    }
    else if ("\r\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(WINDOWS_STRING);
    }
    else if ("\r".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(MACINTOSH_STRING);
    }
    else {
      myLineSeparatorCombo.setSelectedItem(SYSTEM_DEPENDANT_STRING);
    }

    myRightMarginField.setText(String.valueOf(settings.getDefaultRightMargin()));
    myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);

    myAcceptRegularExpressionsCheckBox.setSelected(settings.FORMATTER_TAGS_ACCEPT_REGEXP);
    myEnableFormatterTags.setSelected(settings.FORMATTER_TAGS_ENABLED);

    myFormatterOnTagField.setText(settings.FORMATTER_ON_TAG);
    myFormatterOffTagField.setText(settings.FORMATTER_OFF_TAG);

    setFormatterTagControlsEnabled(settings.FORMATTER_TAGS_ENABLED);

    myAutodetectIndentsBox.setSelected(settings.AUTODETECT_INDENTS);
    myShowDetectedIndentNotification.setEnabled(myAutodetectIndentsBox.isSelected());
    myShowDetectedIndentNotification.setSelected(FileIndentOptionsProvider.isShowNotification());

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.reset(settings);
    }
  }

  private void setFormatterTagControlsEnabled(boolean isEnabled) {
    myFormatterOffTagField.setEnabled(isEnabled);
    myFormatterOnTagField.setEnabled(isEnabled);
    myMarkersPanel.setEnabled(isEnabled);
    myAcceptRegularExpressionsCheckBox.setEnabled(isEnabled);
    myFormatterOffLabel.setEnabled(isEnabled);
    myFormatterOnLabel.setEnabled(isEnabled);
    myMarkerOptionsPanel.setEnabled(isEnabled);
  }

  @Override
  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    //noinspection NullableProblems
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
  }

  @Override
  protected void prepareForReformat(final PsiFile psiFile) {
  }


  @Override
  public Language getDefaultLanguage() {
    return null;
  }

  private static void showError(final JTextField field, final String message) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, MessageType.ERROR.getDefaultIcon(), MessageType.ERROR.getPopupBackground(), null);
    balloonBuilder.setFadeoutTime(1500);
    final Balloon balloon = balloonBuilder.createBalloon();
    final Rectangle rect = field.getBounds();
    final Point p = new Point(0, rect.height);
    final RelativePoint point = new RelativePoint(field, p);
    balloon.show(point, Balloon.Position.below);
    Disposer.register(ProjectManager.getInstance().getDefaultProject(), balloon);
  }
}
