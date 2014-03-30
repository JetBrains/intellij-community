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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.containers.hash.*;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */

public abstract class TabbedLanguageCodeStylePanel extends CodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#" + TabbedLanguageCodeStylePanel.class.getName());

  private CodeStyleAbstractPanel myActiveTab;
  private List<CodeStyleAbstractPanel> myTabs;
  private JPanel myPanel;
  private JTabbedPane myTabbedPane;
  private final PredefinedCodeStyle[] myPredefinedCodeStyles;
  private JPopupMenu myCopyFromMenu;

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

  public void showSetFrom(Object e) {
    final Container component = (Container)e;
    final Component[] components = component.getComponents();
    final Component last = components[components.length - 1];
    initCopyFromMenu();
    myCopyFromMenu.show(last, 0, last.getHeight() + 3);
  }

  private void initCopyFromMenu() {
    if (myCopyFromMenu == null) {
      myCopyFromMenu = new JBPopupMenu();
      setupCopyFromMenu(myCopyFromMenu);
    }
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
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : myTabs) {
      tab.apply(settings);
    }
  }

  @Override
  public void dispose() {
    for (CodeStyleAbstractPanel tab : myTabs) {
      Disposer.dispose(tab);
    }
    super.dispose();
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
  public void setupCopyFromMenu(JPopupMenu copyMenu) {
    super.setupCopyFromMenu(copyMenu);
    if (myPredefinedCodeStyles.length > 0) {
      JMenu langs = new JMenu("Language") {
        @Override
        public void paint(Graphics g) {
          GraphicsUtil.setupAntialiasing(g);
          super.paint(g);
        }
      }; //TODO<rv>: Move to resource bundle
      copyMenu.add(langs);
      fillLanguages(langs);
      JMenu predefined = new JMenu("Predefined Style") {
        @Override
        public void paint(Graphics g) {
          GraphicsUtil.setupAntialiasing(g);
          super.paint(g);
        }
      }; //TODO<rv>: Move to resource bundle
      copyMenu.add(predefined);
      fillPredefined(predefined);
    }
    else {
      fillLanguages(copyMenu);
    }
  }


  private void fillLanguages(JComponent parentMenu) {
      Language[] languages = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings();
      @SuppressWarnings("UnnecessaryFullyQualifiedName")
      java.util.List<JMenuItem> langItems = new ArrayList<JMenuItem>();
      for (final Language lang : languages) {
        if (!lang.equals(getDefaultLanguage())) {
          final String langName = LanguageCodeStyleSettingsProvider.getLanguageName(lang);
          JMenuItem langItem = new JBMenuItem(langName);
          langItem.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
              applyLanguageSettings(lang);
            }
          });
          langItems.add(langItem);
        }
      }
      Collections.sort(langItems, new Comparator<JMenuItem>() {
        @Override
        public int compare(JMenuItem item1, JMenuItem item2) {
          return item1.getText().compareToIgnoreCase(item2.getText());
        }
      });
      for (JMenuItem langItem : langItems) {
        parentMenu.add(langItem);
      }
    }

  private void fillPredefined(JMenuItem parentMenu) {
    for (final PredefinedCodeStyle predefinedCodeStyle : myPredefinedCodeStyles) {
      JMenuItem predefinedItem = new JBMenuItem(predefinedCodeStyle.getName());
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
    final Language language = getDefaultLanguage();
    final List<PredefinedCodeStyle> result = new ArrayList<PredefinedCodeStyle>();

    for (PredefinedCodeStyle codeStyle : PredefinedCodeStyle.EP_NAME.getExtensions()) {
      if (codeStyle.getLanguage().equals(language)) {
        result.add(codeStyle);
      }
    }
    final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());

    if (provider != null) {
      result.addAll(Arrays.asList(provider.getPredefinedCodeStyles()));
    }
    return result.toArray(new PredefinedCodeStyle[result.size()]);
  }


  private void applyLanguageSettings(Language lang) {
    final Project currProject = ProjectUtil.guessCurrentProject(getPanel());
    CodeStyleSettings rootSettings = CodeStyleSettingsManager.getSettings(currProject);
    CommonCodeStyleSettings sourceSettings = rootSettings.getCommonSettings(lang);
    CommonCodeStyleSettings targetSettings = getSettings().getCommonSettings(getDefaultLanguage());
    if (sourceSettings == null || targetSettings == null) return;
    if (!(targetSettings instanceof CodeStyleSettings)) {
      CommonCodeStyleSettingsManager.copy(sourceSettings, targetSettings);
    }
    else {
      Language targetLang = getDefaultLanguage();
      LOG.error((targetLang != null ? targetLang.getDisplayName() : "Unknown") +
                " language plug-in either uses an outdated API or does not initialize" +
                " its own code style settings in LanguageCodeStyleSettingsProvider.getDefaultSettings()." +
                " The operation can not be applied in this case.");
    }
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

  protected class MySpacesPanel extends CodeStyleSpacesPanel {

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

  protected class MyBlankLinesPanel extends CodeStyleBlankLinesPanel {

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

  protected class MyWrappingAndBracesPanel extends WrappingAndBracesPanel {

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

    private final Configurable myConfigurable;
    private JComponent myComponent;

    public ConfigurableWrapper(@NotNull Configurable configurable, CodeStyleSettings settings) {
      super(settings);
      myConfigurable = configurable;

      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          myConfigurable.disposeUIResources();
        }
      });
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
    public void apply(CodeStyleSettings settings) throws ConfigurationException {
      myConfigurable.apply();
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
      return myConfigurable.isModified();
    }

    @Nullable
    @Override
    public JComponent getPanel() {
      if (myComponent == null) {
        myComponent = myConfigurable.createComponent();
      }
      return myComponent;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
      if (myConfigurable instanceof CodeStyleAbstractConfigurable) {
        // when a predefined style is chosen and the configurable is wrapped in a tab,
        // we apply it to CLONED code style settings and then pass them to this method to reset,
        // usual reset() won't work in such case
        ((CodeStyleAbstractConfigurable)myConfigurable).reset(settings);
      }
      else {
        // todo: support   for other configurables
        myConfigurable.reset();
      }
    }
  }

  @Override
  public boolean isCopyFromMenuAvailable() {
    return true;
  }

  @Override
  public Set<String> processListOptions() {
    final Set<String> result = new HashSet<String>();
    for (CodeStyleAbstractPanel tab : myTabs) {
      result.addAll(tab.processListOptions());
    }
    return result;
  }

  //========================================================================================================================================

  protected class MyIndentOptionsWrapper extends CodeStyleAbstractPanel {

    private final IndentOptionsEditor myEditor;
    private final LanguageCodeStyleSettingsProvider myProvider;
    private final JPanel myTopPanel;
    private final JPanel myLeftPanel;
    private final JPanel myRightPanel;

    protected MyIndentOptionsWrapper(CodeStyleSettings settings, LanguageCodeStyleSettingsProvider provider, IndentOptionsEditor editor) {
      super(settings);
      myProvider = provider;
      myTopPanel = new JPanel();
      myTopPanel.setLayout(new BorderLayout(8, 0));
      myLeftPanel = new JPanel(new BorderLayout());
      myTopPanel.add(myLeftPanel, BorderLayout.WEST);
      myRightPanel = new JPanel();
      installPreviewPanel(myRightPanel);
      myEditor = editor;
      if (myEditor != null) {
        JPanel panel = myEditor.createPanel();
        JScrollPane scroll = ScrollPaneFactory.createScrollPane(panel, true);
        scroll.setPreferredSize(new Dimension(panel.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 5, -1));
        myLeftPanel.add(scroll, BorderLayout.CENTER);
      }
      myTopPanel.add(myRightPanel, BorderLayout.CENTER);
    }

    @Override
    protected int getRightMargin() {
      return myProvider.getRightMargin(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS);
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
    protected String getFileExt() {
      if (myProvider != null) {
        String ext = myProvider.getFileExt();
        if (ext != null) return ext;
      }
      return super.getFileExt();
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
