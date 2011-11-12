/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.application.options.codeStyle.*;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */

public abstract class TabbedLanguageCodeStylePanel extends CodeStyleAbstractPanel {

  private CodeStyleAbstractPanel myActiveTab;
  private List<CodeStyleAbstractPanel> myTabs;
  private JPanel myPanel;
  private JTabbedPane myTabbedPane;
  private PredefinedCodeStyle[] myPredefinedCodeStyles;

  protected TabbedLanguageCodeStylePanel(@Nullable Language language, CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(language, currentSettings, settings);
    myPredefinedCodeStyles = getPredefinedStyles();
  }

  /**
   * Initializes all standard tabs: "Tabs and Indents", "Spaces", "Blank Lines" and "Wrapping and Braces" if relevant.
   * For "Tabs and Indents" LanguageCodeStyleSettingsProvider must instantiate its own indent options, for other standard tabs it
   * must return false in usesSharedPreview() method. You can override this method to add your own tabs by calling super.initTabs() and
   * then addTab() methods or selectively add needed tabs with your own implementation.
   * @param settings  Code style settings to be used with initialized panels.
   * @see LanguageCodeStyleSettingsProvider
   * @see #addIndentOptionsTab(com.intellij.psi.codeStyle.CodeStyleSettings)
   * @see #addSpacesTab(com.intellij.psi.codeStyle.CodeStyleSettings)
   * @see #addBlankLinesTab(com.intellij.psi.codeStyle.CodeStyleSettings)
   * @see #addWrappingAndBracesTab(com.intellij.psi.codeStyle.CodeStyleSettings)
   */
  protected void initTabs(CodeStyleSettings settings) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
    addIndentOptionsTab(settings);
    if (provider != null && !provider.usesSharedPreview()) {
      addSpacesTab(settings);
      addWrappingAndBracesTab(settings);
      addBlankLinesTab(settings);
    }
  }

  /**
   * Adds "Tabs and Indents" tab if the language has its own LanguageCodeStyleSettings provider and instantiates indent options in 
   * getDefaultSettings() method.
   * @param settings CodeStyleSettings to be used with "Tabs and Indents" panel.
   */
  protected void addIndentOptionsTab(CodeStyleSettings settings) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
    if (provider != null) {
      IndentOptionsEditor indentOptionsEditor = provider.getIndentOptionsEditor();
      if (indentOptionsEditor != null) {
        MyIndentOptionsWrapper indentOptionsWrapper = new MyIndentOptionsWrapper(settings, provider, indentOptionsEditor);
        addTab(indentOptionsWrapper);
      }
    }
  }

  protected void addSpacesTab(CodeStyleSettings settings) {
    addTab(new MySpacesPanel(settings));
  }

  protected void addBlankLinesTab(CodeStyleSettings settings) {
    addTab(new MyBlankLinesPanel(settings));
  }

  protected void addWrappingAndBracesTab(CodeStyleSettings settings) {
    addTab(new MyWrappingAndBracesPanel(settings));
  }

  private void ensureTabs() {
    if (myTabs == null) {
      myPanel = new JPanel();
      myPanel.setLayout(new BorderLayout());
      myTabbedPane = new JBTabbedPane();
      myTabs = new ArrayList<CodeStyleAbstractPanel>();
      myPanel.add(myTabbedPane);
      initTabs(getSettings());
    }
    assert !myTabs.isEmpty();
  }

  /**
   * Adds a tab with the given CodeStyleAbstractPanel. Tab title is taken from getTabTitle() method.
   * @param tab The panel to use in a tab.
   */
  protected final void addTab(CodeStyleAbstractPanel tab) {
    myTabs.add(tab);
    tab.setShouldUpdatePreview(true);
    addPanelToWatch(tab.getPanel());
    myTabbedPane.addTab(tab.getTabTitle(), tab.getPanel());
    if (myActiveTab == null) {
      myActiveTab = tab;
    }
  }

  private void addTab(Configurable configurable) {
    ConfigurableWrapper wrapper = new ConfigurableWrapper(configurable, getSettings());
    addTab(wrapper);
  }

  /**
   * Creates and adds a tab from CodeStyleSettingsProvider. The provider may return false in hasSettingsPage() method in order not to be
   * shown at top level of code style settings.
   * @param provider The provider used to create a settings page.
   */
  protected final void createTab(CodeStyleSettingsProvider provider) {
    if (provider.hasSettingsPage()) return;
    Configurable configurable = provider.createSettingsPage(getCurrentSettings(), getSettings());
    addTab(configurable);
  }

  @Override
  public final void setModel(CodeStyleSchemesModel model) {
    super.setModel(model);
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.setModel(model);
    }
  }

  @Override
  protected int getRightMargin() {
    ensureTabs();
    return myActiveTab.getRightMargin();
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    ensureTabs();
    return myActiveTab.createHighlighter(scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    ensureTabs();
    return myActiveTab.getFileType();
  }

  @Override
  protected String getPreviewText() {
    ensureTabs();
    return myActiveTab.getPreviewText();
  }

  @Override
  protected void updatePreview(boolean useDefaultSample) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.updatePreview(useDefaultSample);
    }
  }

  @Override
  public void onSomethingChanged() {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.setShouldUpdatePreview(true);
      tab.onSomethingChanged();
    }
  }

  @Override
  protected void somethingChanged() {
    super.somethingChanged();
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.apply(settings);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    for (CodeStyleAbstractPanel tab : myTabs) {
      Disposer.dispose(tab);
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      if (tab.isModified(settings)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.resetImpl(settings);
    }
  }


  @Override
  public void setupCopyFromMenu(Menu copyMenu) {
    super.setupCopyFromMenu(copyMenu);
    if (myPredefinedCodeStyles.length > 0) {
      Menu langs = new Menu("Language"); //TODO<rv>: Move to resource bundle
      copyMenu.add(langs);
      fillLanguages(langs);
      Menu predefined = new Menu("Predefined Style"); //TODO<rv>: Move to resource bundle
      copyMenu.add(predefined);
      fillPredefined(predefined);
    }
    else {
      fillLanguages(copyMenu);
    }
  }


  private void fillLanguages(Menu parentMenu) {
      Language[] languages = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings();
      @SuppressWarnings("UnnecessaryFullyQualifiedName")
      java.util.List<MenuItem> langItems = new ArrayList<MenuItem>();
      for (final Language lang : languages) {
        if (!lang.equals(getDefaultLanguage())) {
          final String langName = LanguageCodeStyleSettingsProvider.getLanguageName(lang);
          MenuItem langItem = new MenuItem(langName);
          langItem.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
              applyLanguageSettings(lang);
            }
          });
          langItems.add(langItem);
        }
      }
      Collections.sort(langItems, new Comparator<MenuItem>() {
        @Override
        public int compare(MenuItem item1, MenuItem item2) {
          return item1.getLabel().compareToIgnoreCase(item2.getLabel());
        }
      });
      for (MenuItem langItem : langItems) {
        parentMenu.add(langItem);
      }
    }

  private void fillPredefined(Menu parentMenu) {
    for (final PredefinedCodeStyle predefinedCodeStyle : myPredefinedCodeStyles) {
      MenuItem predefinedItem = new MenuItem(predefinedCodeStyle.getName());
      parentMenu.add(predefinedItem);
      predefinedItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          applyPredefinedStyle(predefinedCodeStyle.getName());
        }
      });
    }
  }

  private PredefinedCodeStyle[] getPredefinedStyles() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
    if (provider == null) return new PredefinedCodeStyle[0];
    return provider.getPredefinedCodeStyles();
  }


  private void applyLanguageSettings(Language lang) {
    final Project currProject = ProjectUtil.guessCurrentProject(getPanel());
    CodeStyleSettings rootSettings = CodeStyleSettingsManager.getSettings(currProject);
    CommonCodeStyleSettings sourceSettings = rootSettings.getCommonSettings(lang);
    CommonCodeStyleSettings targetSettings = getSettings().getCommonSettings(getDefaultLanguage());
    if (sourceSettings == null || targetSettings == null) return;
    CommonCodeStyleSettingsManager.copy(sourceSettings, targetSettings);
    reset(getSettings());
    onSomethingChanged();
  }

  private void applyPredefinedStyle(String styleName) {
    for (PredefinedCodeStyle style : myPredefinedCodeStyles) {
      if (style.getName().equals(styleName)) {
        applyPredefinedSettings(style);
      }
    }
  }

//========================================================================================================================================

  private class MySpacesPanel extends CodeStyleSpacesPanel {

    public MySpacesPanel(CodeStyleSettings settings) {
      super(settings);
      setPanelLanguage(TabbedLanguageCodeStylePanel.this.getDefaultLanguage());
    }

    @Override
    protected void installPreviewPanel(JPanel previewPanel) {
      previewPanel.setLayout(new BorderLayout());
      previewPanel.add(getEditor().getComponent(), BorderLayout.CENTER);
    }

    @Override
    protected void customizeSettings() {
      customizePanel(this);
    }

    @Override
    protected boolean shouldHideOptions() {
      return true;
    }
  }

  private class MyBlankLinesPanel extends CodeStyleBlankLinesPanel {

    public MyBlankLinesPanel(CodeStyleSettings settings) {
      super(settings);
      setPanelLanguage(TabbedLanguageCodeStylePanel.this.getDefaultLanguage());
    }

    @Override
    protected void customizeSettings() {
      customizePanel(this);
    }

    @Override
    protected void installPreviewPanel(JPanel previewPanel) {
      previewPanel.setLayout(new BorderLayout());
      previewPanel.add(getEditor().getComponent(), BorderLayout.CENTER);
    }

  }

  private class MyWrappingAndBracesPanel extends WrappingAndBracesPanel {

    public MyWrappingAndBracesPanel(CodeStyleSettings settings) {
      super(settings);
      setPanelLanguage(TabbedLanguageCodeStylePanel.this.getDefaultLanguage());
    }

    @Override
    protected void customizeSettings() {
      customizePanel(this);
    }

    @Override
    protected void installPreviewPanel(JPanel previewPanel) {
      previewPanel.setLayout(new BorderLayout());
      previewPanel.add(getEditor().getComponent(), BorderLayout.CENTER);
    }
  }

  private void customizePanel(MultilanguageCodeStyleAbstractPanel panel) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
    if (provider != null) {
      provider.customizeSettings(panel, panel.getSettingsType());
    }
  }


  //========================================================================================================================================

  private class ConfigurableWrapper extends CodeStyleAbstractPanel {

    private Configurable myConfigurable;

    public ConfigurableWrapper(@NotNull Configurable configurable, CodeStyleSettings settings) {
      super(settings);
      myConfigurable = configurable;
    }

    @Override
    protected int getRightMargin() {
      return 0;
    }

    @Nullable
    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
      return null;
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    @Override
    protected FileType getFileType() {
      Language language = getDefaultLanguage();
      return language != null ? language.getAssociatedFileType() : FileTypes.PLAIN_TEXT;
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }

    @Override
    protected String getTabTitle() {
      return myConfigurable.getDisplayName();
    }

    @Override
    protected String getPreviewText() {
      return null;
    }

    @Override
    public void apply(CodeStyleSettings settings) {
      try {
        myConfigurable.apply();
      }
      catch (ConfigurationException e) {
        // Ignore
      }
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
      return myConfigurable.isModified();
    }

    @Nullable
    @Override
    public JComponent getPanel() {
      return myConfigurable.createComponent();
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
      myConfigurable.reset();
    }
  }
  
  //========================================================================================================================================
  
  private class MyIndentOptionsWrapper extends CodeStyleAbstractPanel {

    private final IndentOptionsEditor myEditor;
    private final LanguageCodeStyleSettingsProvider myProvider;
    private JPanel myTopPanel;
    private JPanel myLeftPanel;
    private JPanel myRightPanel;

    protected MyIndentOptionsWrapper(CodeStyleSettings settings, LanguageCodeStyleSettingsProvider provider, IndentOptionsEditor editor) {
      super(settings);
      myProvider = provider;
      myTopPanel = new JPanel();
      myTopPanel.setLayout(new BorderLayout());
      myLeftPanel = new JPanel();
      myTopPanel.add(myLeftPanel, BorderLayout.WEST);
      myRightPanel = new JPanel();
      installPreviewPanel(myRightPanel);
      myEditor = editor;
      if (myEditor != null) {
        myLeftPanel.add(myEditor.createPanel());
      }
      myTopPanel.add(myRightPanel, BorderLayout.CENTER);
    }

    @Override
    protected int getRightMargin() {
      return getSettings().RIGHT_MARGIN;
    }

    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
      //noinspection NullableProblems
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    @Override
    protected FileType getFileType() {
      Language language = TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
      return language != null ? language.getAssociatedFileType() : FileTypes.PLAIN_TEXT;
    }

    @Override
    protected String getPreviewText() {
      return myProvider != null ? myProvider.getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS) : "Loading...";
    }

    @Override
    public void apply(CodeStyleSettings settings) {
      CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(settings);
      if (indentOptions == null) return;
      myEditor.apply(settings, indentOptions);
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
      CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(settings);
      if (indentOptions == null) return false;
      return myEditor.isModified(settings, indentOptions);
    }

    @Override
    public JComponent getPanel() {
      return myTopPanel;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
      CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(settings);
      if (indentOptions == null) {
        myEditor.setEnabled(false);
        indentOptions = settings.getIndentOptions(myProvider.getLanguage().getAssociatedFileType());
      }
      myEditor.reset(settings, indentOptions);
    }

    @Nullable
    private CommonCodeStyleSettings.IndentOptions getIndentOptions(CodeStyleSettings settings) {
      return settings.getCommonSettings(getDefaultLanguage()).getIndentOptions();
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }

    @Override
    protected String getTabTitle() {
      return ApplicationBundle.message("title.tabs.and.indents");
    }

    @Override
    public void onSomethingChanged() {
      super.onSomethingChanged();
      myEditor.setEnabled(true);
    }

  }
}
