// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
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
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ApiStatus.Internal
public final class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  private static final ExtensionPointName<GeneralCodeStyleOptionsProviderEP> EP_NAME = new ExtensionPointName<>("com.intellij.generalCodeStyleOptionsProvider");

  @SuppressWarnings("UnusedDeclaration")
  private static final Logger LOG = Logger.getInstance(GeneralCodeStylePanel.class);
  private List<GeneralCodeStyleOptionsProvider> myAdditionalOptions;


  private JPanel myPanel;
  private JBTabbedPane myTabbedPane;
  private JPanel myGeneralTab;
  private JPanel myFormatterTab;
  private final JScrollPane         myScrollPane;
  private static int ourSelectedTabIndex = -1;
  private final GeneralCodeStyleGeneralTab generalTab;
  private final GeneralCodeStyleFormatterTab formatterTab;

  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    generalTab = new GeneralCodeStyleGeneralTab(settings);
    formatterTab = new GeneralCodeStyleFormatterTab();
    addPanelToWatch(myPanel);

    myPanel.setBorder(JBUI.Borders.empty());
    myScrollPane = ScrollPaneFactory.createScrollPane(null, true);
    myScrollPane.setViewport(new GradientViewport(myPanel, JBUI.insetsTop(5), true));

    updateGeneralOptionsPanel();

    myGeneralTab.setBorder(JBUI.Borders.empty(10));
    myGeneralTab.add(generalTab.panel, BorderLayout.CENTER);
    myFormatterTab.setBorder(JBUI.Borders.empty(10));
    myFormatterTab.add(formatterTab.panel, BorderLayout.CENTER);
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
    formatterTab.excludedScopesPanel.apply(settings);
    formatterTab.apply(settings);

    settings.LINE_SEPARATOR = generalTab.getLineSeparator();

    settings.setDefaultRightMargin(getRightMargin());
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = generalTab.myCbWrapWhenTypingReachesRightMargin.isSelected();

    settings.FORMATTER_TAGS_ENABLED = formatterTab.enableFormatterTags.isSelected();
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = formatterTab.acceptRegularExpressionsCheckBox.isSelected();

    settings.FORMATTER_OFF_TAG = getTagText(formatterTab.formatterOffTagField, settings.FORMATTER_OFF_TAG);
    settings.setFormatterOffPattern(compilePattern(settings, formatterTab.formatterOffTagField, settings.FORMATTER_OFF_TAG));

    settings.FORMATTER_ON_TAG = getTagText(formatterTab.formatterOnTagField, settings.FORMATTER_ON_TAG);
    settings.setFormatterOnPattern(compilePattern(settings, formatterTab.formatterOnTagField, settings.FORMATTER_ON_TAG));

    settings.AUTODETECT_INDENTS = generalTab.myAutodetectIndentsBox.isSelected();

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.apply(settings);
    }
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

    if (formatterTab.excludedScopesPanel.isModified(settings)) return true;
    if (formatterTab.isModified(settings)) return true;

    if (!Objects.equals(generalTab.getLineSeparator(), settings.LINE_SEPARATOR)) {
      return true;
    }

    if (settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN ^ generalTab.myCbWrapWhenTypingReachesRightMargin.isSelected()) {
      return true;
    }

    if (getRightMargin() != settings.getDefaultRightMargin()) return true;

    if (formatterTab.enableFormatterTags.isSelected()) {
      if (
        !settings.FORMATTER_TAGS_ENABLED ||
        settings.FORMATTER_TAGS_ACCEPT_REGEXP != formatterTab.acceptRegularExpressionsCheckBox.isSelected() ||
        !StringUtil.equals(formatterTab.formatterOffTagField.getText(), settings.FORMATTER_OFF_TAG) ||
        !StringUtil.equals(formatterTab.formatterOnTagField.getText(), settings.FORMATTER_ON_TAG)) return true;
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

    formatterTab.excludedScopesPanel.reset(settings);
    formatterTab.reset(settings);

    generalTab.setLineSeparator(settings.LINE_SEPARATOR);
    generalTab.myRightMarginField.setValue(settings.getDefaultRightMargin());
    generalTab.myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);

    formatterTab.acceptRegularExpressionsCheckBox.setSelected(settings.FORMATTER_TAGS_ACCEPT_REGEXP);
    formatterTab.enableFormatterTags.setSelected(settings.FORMATTER_TAGS_ENABLED);

    formatterTab.formatterOnTagField.setText(settings.FORMATTER_ON_TAG);
    formatterTab.formatterOffTagField.setText(settings.FORMATTER_OFF_TAG);

    generalTab.myAutodetectIndentsBox.setSelected(settings.AUTODETECT_INDENTS);

    for (GeneralCodeStyleOptionsProvider option : myAdditionalOptions) {
      option.reset(settings);
    }
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
    formatterTab.excludedScopesPanel.setSchemesModel(model);
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
