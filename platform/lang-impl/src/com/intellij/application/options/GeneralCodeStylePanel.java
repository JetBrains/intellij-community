// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.application.options.codeStyle.excludedFiles.ExcludedGlobPatternsPanel;
import com.intellij.application.options.codeStyle.excludedFiles.ExcludedScopesPanel;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.fields.CommaSeparatedIntegersField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  private static final ExtensionPointName<GeneralCodeStyleOptionsProviderEP> EP_NAME = new ExtensionPointName<>("com.intellij.generalCodeStyleOptionsProvider");

  @SuppressWarnings("UnusedDeclaration")
  private static final Logger LOG = Logger.getInstance(GeneralCodeStylePanel.class);
  private List<GeneralCodeStyleOptionsProvider> myAdditionalOptions;

  private IntegerField myRightMarginField;

  private ComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private JBCheckBox myCbWrapWhenTypingReachesRightMargin;
  private JCheckBox myEnableFormatterTags;
  private JTextField myFormatterOnTagField;
  private JTextField myFormatterOffTagField;
  private JCheckBox myAcceptRegularExpressionsCheckBox;
  private JBLabel myFormatterOffLabel;
  private JBLabel myFormatterOnLabel;
  private JPanel myMarkerOptionsPanel;
  private JPanel myAdditionalSettingsPanel;
  private JCheckBox myAutodetectIndentsBox;
  private CommaSeparatedIntegersField myVisualGuides;
  private JBLabel myVisualGuidesHint;
  private JBLabel myLineSeparatorHint;
  private JBLabel myVisualGuidesLabel;
  private JBTabbedPane myTabbedPane;
  private ExcludedGlobPatternsPanel myExcludedPatternsPanel;
  private ExcludedScopesPanel       myExcludedScopesPanel;
  private JPanel myGeneralTab;
  private JPanel myFormatterTab;
  private JBCheckBox myEnableSecondReformat;
  private final JScrollPane         myScrollPane;
  private static int ourSelectedTabIndex = -1;

  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    //noinspection unchecked
    myLineSeparatorCombo.addItem(getSystemDependantString());
    //noinspection unchecked
    myLineSeparatorCombo.addItem(getUnixString());
    //noinspection unchecked
    myLineSeparatorCombo.addItem(getWindowsString());
    //noinspection unchecked
    myLineSeparatorCombo.addItem(getMacintoshString());
    addPanelToWatch(myPanel);

    myRightMarginField.setDefaultValue(settings.getDefaultRightMargin());

    myEnableFormatterTags.addActionListener(__ -> {
      boolean tagsEnabled = myEnableFormatterTags.isSelected();
      setFormatterTagControlsEnabled(tagsEnabled);
    });

    myAutodetectIndentsBox.setBorder(JBUI.Borders.emptyTop(10));

    myPanel.setBorder(JBUI.Borders.empty());
    myScrollPane = ScrollPaneFactory.createScrollPane(null, true);
    myScrollPane.setViewport(new GradientViewport(myPanel, JBUI.insetsTop(5), true));

    myAdditionalSettingsPanel.setLayout(new VerticalFlowLayout(true, true));
    updateGeneralOptionsPanel();

    myVisualGuidesLabel.setText(ApplicationBundle.message("settings.code.style.visual.guides") + ":");
    myVisualGuidesHint.setForeground(JBColor.GRAY);
    myVisualGuidesHint.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    myLineSeparatorHint.setForeground(JBColor.GRAY);
    myLineSeparatorHint.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));

    myGeneralTab.setBorder(JBUI.Borders.empty(15, 15, 0, 0));
    myFormatterTab.setBorder(JBUI.Borders.empty(15, 15, 0, 0));
    myMarkerOptionsPanel.setBorder(JBUI.Borders.emptyTop(10));
    myEnableSecondReformat.setBorder(JBUI.Borders.emptyTop(10));
    if (ourSelectedTabIndex >= 0) {
      myTabbedPane.setSelectedIndex(ourSelectedTabIndex);
    }
    myTabbedPane.addChangeListener(__ -> {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourSelectedTabIndex = myTabbedPane.getSelectedIndex();
    });
    myTabbedPane.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        // The 'Code Style' page has become visible, either the first time, or after switching to some other page in Settings (Preferences) and then back to this page.
        if (ourSelectedTabIndex > 0 && myTabbedPane.getSelectedIndex() != ourSelectedTabIndex) {
          // At the moment of writing this code, it's possible to get to this line of code only via selectFormatterTab(Settings) method.
          myTabbedPane.setSelectedIndex(ourSelectedTabIndex);
        }
      }
    });
    EP_NAME.addExtensionPointListener(
      new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull GeneralCodeStyleOptionsProviderEP extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          updateGeneralOptionsPanel();
        }

        @Override
        public void extensionRemoved(@NotNull GeneralCodeStyleOptionsProviderEP extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          updateGeneralOptionsPanel();
        }
      }, this);
  }

  private void updateGeneralOptionsPanel() {
    myAdditionalSettingsPanel.removeAll();
    myAdditionalOptions = ConfigurableWrapper.createConfigurables(EP_NAME);
    for (GeneralCodeStyleOptionsProvider provider : myAdditionalOptions) {
      JComponent generalSettingsComponent = provider.createComponent();
      if (generalSettingsComponent != null) {
        myAdditionalSettingsPanel.add(Box.createRigidArea(JBUI.size(0, 5)));
        myAdditionalSettingsPanel.add(generalSettingsComponent);
      }
    }
  }


  @Override
  protected int getRightMargin() {
    return myRightMarginField.getValue();
  }

  @Override
  @NotNull
  protected FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }


  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myVisualGuides.validateContent();
    myRightMarginField.validateContent();
    settings.setDefaultSoftMargins(myVisualGuides.getValue());
    myExcludedScopesPanel.apply(settings);
    myExcludedPatternsPanel.apply(settings);

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
    settings.ENABLE_SECOND_REFORMAT = myEnableSecondReformat.isSelected();

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.apply(settings);
    }
  }

  private void createUIComponents() {
    myRightMarginField = new IntegerField(ApplicationBundle.message("editbox.right.margin.columns"), 0, CodeStyleConstraints.MAX_RIGHT_MARGIN);
    myVisualGuides = new CommaSeparatedIntegersField(ApplicationBundle.message("settings.code.style.visual.guides"),
                                                     0, CodeStyleConstraints.MAX_RIGHT_MARGIN,
                                                     ApplicationBundle.message("settings.code.style.visual.guides.optional"));
    myExcludedPatternsPanel = new ExcludedGlobPatternsPanel();
    myExcludedPatternsPanel.setBorder(JBUI.Borders.emptyTop(5));
    myExcludedScopesPanel = new ExcludedScopesPanel();
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
      return defaultValue;
    }
    return fieldText;
  }

  @Nullable
  private String getSelectedLineSeparator() {
    if (getUnixString().equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\n";
    }
    if (getMacintoshString().equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r";
    }
    if (getWindowsString().equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r\n";
    }
    return null;
  }


  @Override
  public boolean isModified(CodeStyleSettings settings) {
    if (!myVisualGuides.getValue().equals(settings.getDefaultSoftMargins())) return true;

    if (myExcludedScopesPanel.isModified(settings)) return true;
    if (myExcludedPatternsPanel.isModified(settings)) return true;

    if (!Objects.equals(getSelectedLineSeparator(), settings.LINE_SEPARATOR)) {
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

    return settings.AUTODETECT_INDENTS != myAutodetectIndentsBox.isSelected() ||
           settings.ENABLE_SECOND_REFORMAT != myEnableSecondReformat.isSelected();
  }

  @Override
  public JComponent getPanel() {
    return myScrollPane;
  }

  @Override
  protected void resetImpl(final @NotNull CodeStyleSettings settings) {
    myVisualGuides.setValue(settings.getDefaultSoftMargins());

    myExcludedScopesPanel.reset(settings);
    myExcludedPatternsPanel.reset(settings);

    String lineSeparator = settings.LINE_SEPARATOR;
    if ("\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(getUnixString());
    }
    else if ("\r\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(getWindowsString());
    }
    else if ("\r".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(getMacintoshString());
    }
    else {
      myLineSeparatorCombo.setSelectedItem(getSystemDependantString());
    }

    myRightMarginField.setValue(settings.getDefaultRightMargin());
    myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);

    myAcceptRegularExpressionsCheckBox.setSelected(settings.FORMATTER_TAGS_ACCEPT_REGEXP);
    myEnableFormatterTags.setSelected(settings.FORMATTER_TAGS_ENABLED);

    myFormatterOnTagField.setText(settings.FORMATTER_ON_TAG);
    myFormatterOffTagField.setText(settings.FORMATTER_OFF_TAG);

    setFormatterTagControlsEnabled(settings.FORMATTER_TAGS_ENABLED);

    myAutodetectIndentsBox.setSelected(settings.AUTODETECT_INDENTS);
    myEnableSecondReformat.setSelected(settings.ENABLE_SECOND_REFORMAT);

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.reset(settings);
    }
  }

  private void setFormatterTagControlsEnabled(boolean isEnabled) {
    myFormatterOffTagField.setEnabled(isEnabled);
    myFormatterOnTagField.setEnabled(isEnabled);
    myAcceptRegularExpressionsCheckBox.setEnabled(isEnabled);
    myFormatterOffLabel.setEnabled(isEnabled);
    myFormatterOnLabel.setEnabled(isEnabled);
    myMarkerOptionsPanel.setEnabled(isEnabled);
  }

  @Override
  protected EditorHighlighter createHighlighter(final @NotNull EditorColorsScheme scheme) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
  }


  @Override
  public Language getDefaultLanguage() {
    return null;
  }

  private static void showError(final JTextField field, final @NlsContexts.PopupContent String message) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, MessageType.ERROR, null);
    balloonBuilder.setFadeoutTime(1500);
    final Balloon balloon = balloonBuilder.createBalloon();
    final Rectangle rect = field.getBounds();
    final Point p = new Point(0, rect.height);
    final RelativePoint point = new RelativePoint(field, p);
    balloon.show(point, Balloon.Position.below);
    Disposer.register(ApplicationManager.getApplication(), balloon);
  }

  @Override
  public void setModel(@NotNull CodeStyleSchemesModel model) {
    super.setModel(model);
    myExcludedScopesPanel.setSchemesModel(model);
  }

  @Override
  public void dispose() {
    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.disposeUIResources();
    }
    super.dispose();
  }

  private static @NlsContexts.ListItem String getSystemDependantString() {
    return ApplicationBundle.message("combobox.crlf.system.dependent");
  }

  private static @NlsContexts.ListItem String getUnixString() {
    return ApplicationBundle.message("combobox.crlf.unix");
  }

  private static @NlsContexts.ListItem String getWindowsString() {
    return ApplicationBundle.message("combobox.crlf.windows");
  }

  private static @NlsContexts.ListItem String getMacintoshString() {
    return ApplicationBundle.message("combobox.crlf.mac");
  }

  public static void selectFormatterTab(@NotNull Settings settings) {
    ourSelectedTabIndex = 1;
    settings.select(settings.find(CodeStyleSchemesConfigurable.CONFIGURABLE_ID));
  }
}
