// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
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


  private JPanel myPanel;
  private JCheckBox myEnableFormatterTags;
  private JTextField myFormatterOnTagField;
  private JTextField myFormatterOffTagField;
  private JCheckBox myAcceptRegularExpressionsCheckBox;
  private JBLabel myFormatterOffLabel;
  private JBLabel myFormatterOnLabel;
  private JPanel myMarkerOptionsPanel;
  private JBTabbedPane myTabbedPane;
  private ExcludedGlobPatternsPanel myExcludedPatternsPanel;
  private ExcludedScopesPanel       myExcludedScopesPanel;
  private JPanel myGeneralTab;
  private JPanel myFormatterTab;
  private final JScrollPane         myScrollPane;
  private static int ourSelectedTabIndex = -1;
  private final GeneralCodeStyleGeneralTab generalTab;

  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    generalTab = new GeneralCodeStyleGeneralTab(settings);
    addPanelToWatch(myPanel);

    myEnableFormatterTags.addActionListener(__ -> {
      boolean tagsEnabled = myEnableFormatterTags.isSelected();
      setFormatterTagControlsEnabled(tagsEnabled);
    });

    myPanel.setBorder(JBUI.Borders.empty());
    myScrollPane = ScrollPaneFactory.createScrollPane(null, true);
    myScrollPane.setViewport(new GradientViewport(myPanel, JBUI.insetsTop(5), true));

    updateGeneralOptionsPanel();

    myGeneralTab.setBorder(JBUI.Borders.empty(15, 15, 0, 0));
    myGeneralTab.add(generalTab.panel, BorderLayout.CENTER);
    myFormatterTab.setBorder(JBUI.Borders.empty(15, 15, 0, 0));
    myMarkerOptionsPanel.setBorder(JBUI.Borders.emptyTop(10));
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
    myAdditionalOptions = ConfigurableWrapper.createConfigurables(EP_NAME);
    generalTab.updateGeneralOptions(myAdditionalOptions);
  }


  @Override
  protected int getRightMargin() {
    return generalTab.myRightMarginField.getValue();
  }

  @Override
  protected @NotNull FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }


  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    generalTab.myVisualGuides.validateContent();
    generalTab.myRightMarginField.validateContent();
    settings.setDefaultSoftMargins(generalTab.myVisualGuides.getValue());
    myExcludedScopesPanel.apply(settings);
    myExcludedPatternsPanel.apply(settings);

    settings.LINE_SEPARATOR = generalTab.getLineSeparator();

    settings.setDefaultRightMargin(getRightMargin());
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = generalTab.myCbWrapWhenTypingReachesRightMargin.isSelected();

    settings.FORMATTER_TAGS_ENABLED = myEnableFormatterTags.isSelected();
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = myAcceptRegularExpressionsCheckBox.isSelected();

    settings.FORMATTER_OFF_TAG = getTagText(myFormatterOffTagField, settings.FORMATTER_OFF_TAG);
    settings.setFormatterOffPattern(compilePattern(settings, myFormatterOffTagField, settings.FORMATTER_OFF_TAG));

    settings.FORMATTER_ON_TAG = getTagText(myFormatterOnTagField, settings.FORMATTER_ON_TAG);
    settings.setFormatterOnPattern(compilePattern(settings, myFormatterOnTagField, settings.FORMATTER_ON_TAG));

    settings.AUTODETECT_INDENTS = generalTab.myAutodetectIndentsBox.isSelected();

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.apply(settings);
    }
  }

  private void createUIComponents() {
    myExcludedPatternsPanel = new ExcludedGlobPatternsPanel();
    myExcludedPatternsPanel.setBorder(JBUI.Borders.emptyTop(5));
    myExcludedScopesPanel = new ExcludedScopesPanel();
  }

  private static @Nullable Pattern compilePattern(CodeStyleSettings settings, JTextField field, String patternText) {
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

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    if (!generalTab.myVisualGuides.getValue().equals(settings.getDefaultSoftMargins())) return true;

    if (myExcludedScopesPanel.isModified(settings)) return true;
    if (myExcludedPatternsPanel.isModified(settings)) return true;

    if (!Objects.equals(generalTab.getLineSeparator(), settings.LINE_SEPARATOR)) {
      return true;
    }

    if (settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN ^ generalTab.myCbWrapWhenTypingReachesRightMargin.isSelected()) {
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

    return settings.AUTODETECT_INDENTS != generalTab.myAutodetectIndentsBox.isSelected();
  }

  @Override
  public JComponent getPanel() {
    return myScrollPane;
  }

  @Override
  protected void resetImpl(final @NotNull CodeStyleSettings settings) {
    generalTab.myVisualGuides.setValue(settings.getDefaultSoftMargins());

    myExcludedScopesPanel.reset(settings);
    myExcludedPatternsPanel.reset(settings);

    generalTab.setLineSeparator(settings.LINE_SEPARATOR);
    generalTab.myRightMarginField.setValue(settings.getDefaultRightMargin());
    generalTab.myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);

    myAcceptRegularExpressionsCheckBox.setSelected(settings.FORMATTER_TAGS_ACCEPT_REGEXP);
    myEnableFormatterTags.setSelected(settings.FORMATTER_TAGS_ENABLED);

    myFormatterOnTagField.setText(settings.FORMATTER_ON_TAG);
    myFormatterOffTagField.setText(settings.FORMATTER_OFF_TAG);

    setFormatterTagControlsEnabled(settings.FORMATTER_TAGS_ENABLED);

    generalTab.myAutodetectIndentsBox.setSelected(settings.AUTODETECT_INDENTS);

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


  public static void selectFormatterTab(@NotNull Settings settings) {
    ourSelectedTabIndex = 1;
    settings.select(settings.find(CodeStyleSchemesConfigurable.CONFIGURABLE_ID));
  }
}
