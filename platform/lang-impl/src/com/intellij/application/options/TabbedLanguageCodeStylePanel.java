// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleBlankLinesPanel;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.application.options.codeStyle.CodeStyleSpacesPanel;
import com.intellij.application.options.codeStyle.WrappingAndBracesPanel;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Weighted;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static java.util.Arrays.stream;

public abstract class TabbedLanguageCodeStylePanel extends CodeStyleAbstractPanel {
  private CodeStyleAbstractPanel myActiveTab;
  private List<CodeStyleAbstractPanel> tabs;
  private JPanel myPanel;
  private TabbedPaneWrapper myTabbedPane;
  private final PredefinedCodeStyle[] myPredefinedCodeStyles;
  private JPopupMenu myCopyFromMenu;
  private @Nullable TabChangeListener myListener;
  private final EventDispatcher<PredefinedCodeStyleListener> myPredefinedCodeStyleEventDispatcher = EventDispatcher.create(PredefinedCodeStyleListener.class);

  private Ref<LanguageCodeStyleSettingsProvider> myProviderRef;

  protected TabbedLanguageCodeStylePanel(@Nullable Language language, CodeStyleSettings currentSettings, @NotNull CodeStyleSettings settings) {
    super(language, currentSettings, settings);
    myPredefinedCodeStyles = getPredefinedStyles();
    CodeStyleSettingsProvider.EXTENSION_POINT_NAME.addExtensionPointListener(
      new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull CodeStyleSettingsProvider extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          if (!extension.hasSettingsPage() && getDefaultLanguage() == extension.getLanguage()) {
            createTab(extension);
          }
        }

        @Override
        public void extensionRemoved(@NotNull CodeStyleSettingsProvider extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          if (!extension.hasSettingsPage() && getDefaultLanguage() == extension.getLanguage()) {
            final String tabTitle = extension.getConfigurableDisplayName();
            for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
              if (myTabbedPane.getTitleAt(i).equals(tabTitle)) {
                myTabbedPane.removeTabAt(i);
                tabs.stream().filter(
                  panel -> panel.getTabTitle().equals(tabTitle)
                ).findFirst().ifPresent(panel -> tabs.remove(panel));
                return;
              }
            }
          }
        }
      }, this
    );
  }

  /**
   * Initializes all standard tabs: "Tabs and Indents", "Spaces", "Blank Lines" and "Wrapping and Braces" if relevant.
   * For "Tabs and Indents" LanguageCodeStyleSettingsProvider must instantiate its own indent options, for other standard tabs it
   * must return false in usesSharedPreview() method. You can override this method to add your own tabs by calling super.initTabs() and
   * then addTab() methods or selectively add needed tabs with your own implementation.
   * @param settings  Code style settings to be used with initialized panels.
   * @see LanguageCodeStyleSettingsProvider
   * @see #addIndentOptionsTab(CodeStyleSettings)
   * @see #addSpacesTab(CodeStyleSettings)
   * @see #addBlankLinesTab(CodeStyleSettings)
   * @see #addWrappingAndBracesTab(CodeStyleSettings)
   */
  protected void initTabs(CodeStyleSettings settings) {
    addIndentOptionsTab(settings);
    if (getProvider() != null) {
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
    if (getProvider() != null) {
      IndentOptionsEditor indentOptionsEditor = getProvider().getIndentOptionsEditor();
      if (indentOptionsEditor != null) {
        MyIndentOptionsWrapper indentOptionsWrapper = new MyIndentOptionsWrapper(settings, indentOptionsEditor);
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

  protected void ensureTabs() {
    if (tabs == null) {
      myPanel = new JPanel();
      myPanel.setLayout(new BorderLayout());
      myTabbedPane = new TabbedPaneWrapper(this);
      myTabbedPane.addChangeListener(__ -> {
        if (myListener != null) {
          String title = myTabbedPane.getSelectedTitle();
          if (title != null) {
            myListener.tabChanged(this, title);
          }
        }
      });
      tabs = new ArrayList<>();
      myPanel.add(myTabbedPane.getComponent());
      initTabs(getSettings());
    }
    assert !tabs.isEmpty();
  }

  public void showSetFrom(Component component) {
    initCopyFromMenu();
    DefaultActionGroup group = new DefaultActionGroup();
    JBTreeTraverser<Component> traverser = JBTreeTraverser.<Component>of(
      o -> o instanceof JMenu ? new Component[] { new TitledSeparator(((JMenu)o).getText()), ((JMenu)o).getPopupMenu()} :
           o instanceof JPopupMenu ? ((JPopupMenu)o).getComponents() : null)
        .withRoot(myCopyFromMenu);
    for (Component c : traverser.traverse(TreeTraversal.LEAVES_DFS)) {
      if (c instanceof JSeparator) {
        group.addSeparator();
      }
      else if (c instanceof TitledSeparator) {
        group.addSeparator(((TitledSeparator)c).getText());
      }
      else if (c instanceof JMenuItem) {
        group.add(new DumbAwareAction(((JMenuItem)c).getText(), "", ObjectUtils.notNull(((JMenuItem)c).getIcon(), EmptyIcon.ICON_16)) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            ((JMenuItem)c).doClick();
          }
        });
      }
    }
    int maxRows = group.getChildrenCount() > 17 ? 15 : -1;
    DataContext dataContext = DataManager.getInstance().getDataContext(component);
    JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false, null, maxRows, null, "popup@TabbedLanguageCodeStylePanel")
    .showUnderneathOf(component);
  }

  private void initCopyFromMenu() {
    if (myCopyFromMenu == null) {
      myCopyFromMenu = new JBPopupMenu();
      myCopyFromMenu.setFocusable(false);
      setupCopyFromMenu(myCopyFromMenu);
    }
  }

  /**
   * Adds a tab with the given CodeStyleAbstractPanel. Tab title is taken from getTabTitle() method.
   * @param tab The panel to use in a tab.
   */
  protected final void addTab(CodeStyleAbstractPanel tab) {
    tabs.add(tab);
    tab.setShouldUpdatePreview(true);
    addPanelToWatch(tab.getPanel());
    myTabbedPane.addTab(tab.getTabTitle(), tab.getPanel());
    if (myActiveTab == null) {
      myActiveTab = tab;
    }
  }

  private void addTab(Configurable configurable) {
    CodeStyleConfigurableWrapperPanel wrapper = new CodeStyleConfigurableWrapperPanel(configurable, getSettings());
    addTab(wrapper);
  }

  /**
   * Creates and adds a tab from CodeStyleSettingsProvider. The provider may return false in hasSettingsPage() method in order not to be
   * shown at top level of code style settings.
   * @param provider The provider used to create a settings page.
   */
  protected final void createTab(CodeStyleSettingsProvider provider) {
    if (provider.hasSettingsPage()) return;
    Configurable configurable = provider.createConfigurable(getCurrentSettings(), getSettings());
    addTab(configurable);
  }

  @Override
  public final void setModel(@NotNull CodeStyleSchemesModel model) {
    super.setModel(model);
    ensureTabs();
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.setModel(model);
    }
  }

  @Override
  protected int getRightMargin() {
    ensureTabs();
    return myActiveTab.getRightMargin();
  }

  @Override
  protected EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    ensureTabs();
    return myActiveTab.createHighlighter(scheme);
  }

  @Override
  protected @NotNull FileType getFileType() {
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
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.updatePreview(useDefaultSample);
    }
  }

  @Override
  public void onSomethingChanged() {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.setShouldUpdatePreview(true);
      tab.onSomethingChanged();
    }
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.apply(settings);
    }
  }

  @Override
  public void dispose() {
    for (CodeStyleAbstractPanel tab : tabs) {
      Disposer.dispose(tab);
    }
    super.dispose();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : tabs) {
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
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    ensureTabs();
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.resetImpl(settings);
    }
  }


  @Override
  public void setupCopyFromMenu(@NotNull JPopupMenu copyMenu) {
    super.setupCopyFromMenu(copyMenu);
    if (myPredefinedCodeStyles.length > 0) {
      fillPredefinedStylesAndLanguages(copyMenu);
    }
    else {
      fillLanguages(copyMenu);
    }
  }

  private void fillPredefinedStylesAndLanguages(JPopupMenu copyMenu) {
    fillPredefinedStyles(copyMenu);
    LanguageCodeStyleSettingsProvider provider = getProvider();
    int n = 0;
    if (provider != null) n = provider.getApplicableLanguages().size();
    if (n > 0) {
      copyMenu.addSeparator();
      if (n <= 15) {
        fillLanguages(copyMenu);
      }
      else {
        JMenu langs = new JMenu(ApplicationBundle.message("code.style.set.from.menu.language")) {
          @Override
          public void paint(Graphics g) {
            GraphicsUtil.setupAntialiasing(g);
            super.paint(g);
          }
        };
        copyMenu.add(langs);
        fillLanguages(langs);
      }
    }
  }

  private void fillLanguages(JComponent parentMenu) {
    List<Language> languages = getProvider() != null ? getProvider().getApplicableLanguages() : Collections.emptyList();
    for (final Language lang : languages) {
      if (!lang.equals(getDefaultLanguage())) {
        final String langName = LanguageCodeStyleSettingsProvider.getLanguageName(lang);
        JMenuItem langItem = new JBMenuItem(langName);
        langItem.addActionListener(__ -> applyLanguageSettings(lang));
        parentMenu.add(langItem);
      }
    }
  }

  private void fillPredefinedStyles(JComponent parentMenu) {
    for (final PredefinedCodeStyle predefinedCodeStyle : myPredefinedCodeStyles) {
      JMenuItem predefinedItem = new JBMenuItem(predefinedCodeStyle.getName());
      parentMenu.add(predefinedItem);
      predefinedItem.addActionListener(__ -> applyPredefinedStyle(predefinedCodeStyle.getName()));
    }
  }

  protected void addPredefinedCodeStyleListener(@NotNull PredefinedCodeStyleListener listener) {
    myPredefinedCodeStyleEventDispatcher.addListener(listener, this);
  }

  private PredefinedCodeStyle[] getPredefinedStyles() {
    final Language language = getDefaultLanguage();
    if (language == null) return PredefinedCodeStyle.EMPTY_ARRAY;

    PredefinedCodeStyle[] predefinedStyles = PredefinedCodeStyle.EP_NAME.getExtensions();
    PredefinedCodeStyle[] styles = stream(predefinedStyles)
      .filter(s -> s.isApplicableToLanguage(language))
      .toArray(n -> new PredefinedCodeStyle[n]);
    if (styles.length >= 2 && ContainerUtil.exists(styles, s -> s instanceof Weighted)) {
      Arrays.sort(styles, WEIGHTED_COMPARATOR);
    }
    
    return styles;
  }

  private static final class WeightedComparator implements Comparator<Object> {
    @Override
    public int compare(Object o1, Object o2) {
      double w1 = o1 instanceof Weighted ? ((Weighted)o1).getWeight() : Double.POSITIVE_INFINITY;
      double w2 = o2 instanceof Weighted ? ((Weighted)o2).getWeight() : Double.POSITIVE_INFINITY;
      return Double.compare(w1, w2);
    }
  }

  private static final WeightedComparator WEIGHTED_COMPARATOR = new WeightedComparator();


  private void applyLanguageSettings(Language lang) {
    final Project currProject = ProjectUtil.guessCurrentProject(getPanel());
    CodeStyleSettings rootSettings = CodeStyle.getSettings(currProject);
    CodeStyleSettings targetSettings = getSettings();

    applyLanguageSettings(lang, rootSettings, targetSettings);
    reset(targetSettings);
    onSomethingChanged();
  }

  protected void applyLanguageSettings(Language lang, CodeStyleSettings rootSettings, CodeStyleSettings targetSettings) {
    CommonCodeStyleSettings sourceCommonSettings = rootSettings.getCommonSettings(lang);
    CommonCodeStyleSettings targetCommonSettings = targetSettings.getCommonSettings(getDefaultLanguage());
    targetCommonSettings.copyFrom(sourceCommonSettings);
  }

  private void applyPredefinedStyle(String styleName) {
    for (PredefinedCodeStyle style : myPredefinedCodeStyles) {
      if (style.getName().equals(styleName)) {
        applyPredefinedSettings(style);
        myPredefinedCodeStyleEventDispatcher.getMulticaster().styleApplied(style);
      }
    }
  }

  private @Nullable LanguageCodeStyleSettingsProvider getProvider() {
    if (myProviderRef == null) {
      myProviderRef = Ref.create(LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage()));
    }
    return myProviderRef.get();
  }

//========================================================================================================================================

  protected class MySpacesPanel extends CodeStyleSpacesPanel {

    public MySpacesPanel(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected boolean shouldHideOptions() {
      return true;
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }
  }

  final class MyBlankLinesPanel extends CodeStyleBlankLinesPanel {
    MyBlankLinesPanel(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }
  }

  protected class MyWrappingAndBracesPanel extends WrappingAndBracesPanel {

    public MyWrappingAndBracesPanel(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }
  }


  //========================================================================================================================================

  private final class CodeStyleConfigurableWrapperPanel extends CodeStyleAbstractPanel {

    private final Configurable myConfigurable;
    private JComponent myComponent;

    CodeStyleConfigurableWrapperPanel(@NotNull Configurable configurable, @NotNull CodeStyleSettings settings) {
      super(settings);
      myConfigurable = configurable;

      Disposer.register(this, () -> myConfigurable.disposeUIResources());
    }

    @Override
    protected int getRightMargin() {
      return 0;
    }

    @Override
    protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
      return null;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected @NotNull FileType getFileType() {
      Language language = getDefaultLanguage();
      return language != null ? language.getAssociatedFileType() : FileTypes.PLAIN_TEXT;
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }

    @Override
    protected @TabTitle @NotNull String getTabTitle() {
      return myConfigurable.getDisplayName();
    }

    @Override
    protected String getPreviewText() {
      return null;
    }

    @Override
    public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
      if (myConfigurable instanceof CodeStyleConfigurable) {
        ((CodeStyleConfigurable)myConfigurable).apply(settings);
      }
      else {
        myConfigurable.apply();
      }
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
      return myConfigurable.isModified();
    }

    @Override
    public @Nullable JComponent getPanel() {
      if (myComponent == null) {
        myComponent = myConfigurable.createComponent();
      }
      return myComponent;
    }

    @Override
    protected void resetImpl(@NotNull CodeStyleSettings settings) {
      if (myConfigurable instanceof CodeStyleConfigurable) {
        ((CodeStyleConfigurable)myConfigurable).reset(settings);
      }
      else {
        myConfigurable.reset();
      }
    }
  }

  @Override
  public @NotNull OptionsContainingConfigurable getOptionIndexer() {
    return new OptionsContainingConfigurable() {
      @Override
      public @NotNull Map<String, Set<String>> processListOptionsWithPaths() {
        Map<String, Set<String>> result = new HashMap<>(tabs.size());
        for (CodeStyleAbstractPanel tab : tabs) {
          result.put(tab.getTabTitle(), tab.processListOptions());
        }
        return result;
      }
    };
  }

  //========================================================================================================================================

  protected class MyIndentOptionsWrapper extends CodeStyleAbstractPanel {
    private final IndentOptionsEditor myEditor;
    private final JPanel myTopPanel = new JPanel(new BorderLayout());

    protected MyIndentOptionsWrapper(CodeStyleSettings settings, IndentOptionsEditor editor) {
      super(settings);
      JPanel leftPanel = new JPanel(new BorderLayout());
      myTopPanel.add(leftPanel, BorderLayout.WEST);
      JPanel rightPanel = new JPanel();
      installPreviewPanel(rightPanel);
      myEditor = editor;
      if (myEditor != null) {
        JPanel panel = myEditor.createPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = ScrollPaneFactory.createScrollPane(panel, true);
        scroll.setPreferredSize(new Dimension(panel.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 5, -1));
        leftPanel.add(scroll, BorderLayout.CENTER);
      }
      myTopPanel.add(rightPanel, BorderLayout.CENTER);
    }

    @Override
    protected int getRightMargin() {
      return getProvider() != null ? getProvider().getRightMargin(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS) : -1;
    }

    @Override
    protected EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected @NotNull FileType getFileType() {
      Language language = TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
      return language != null ? language.getAssociatedFileType() : FileTypes.PLAIN_TEXT;
    }

    @Override
    protected String getPreviewText() {
      return getProvider() != null ? getProvider().getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS) : "";
    }

    @Override
    protected @NotNull String getFileExt() {
      LanguageCodeStyleSettingsProvider provider = getProvider();
      if (provider != null) {
        String ext = provider.getFileExt();
        if (ext != null) return ext;
      }
      return super.getFileExt();
    }

    @Override
    public void apply(@NotNull CodeStyleSettings settings) {
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
    protected void resetImpl(@NotNull CodeStyleSettings settings) {
      CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(settings);
      if (indentOptions == null && getProvider() != null) {
        myEditor.setEnabled(false);
        indentOptions = settings.getIndentOptions(getProvider().getLanguage().getAssociatedFileType());
      }
      assert indentOptions != null;
      myEditor.reset(settings, indentOptions);
    }

    protected @Nullable CommonCodeStyleSettings.IndentOptions getIndentOptions(CodeStyleSettings settings) {
      return settings.getCommonSettings(getDefaultLanguage()).getIndentOptions();
    }

    @Override
    public Language getDefaultLanguage() {
      return TabbedLanguageCodeStylePanel.this.getDefaultLanguage();
    }

    @Override
    protected @TabTitle @NotNull String getTabTitle() {
      return ApplicationBundle.message("title.tabs.and.indents");
    }

    @Override
    public void onSomethingChanged() {
      super.onSomethingChanged();
      myEditor.setEnabled(true);
    }

  }

  @FunctionalInterface
  public interface TabChangeListener {
    void tabChanged(@NotNull TabbedLanguageCodeStylePanel source, @NotNull String tabTitle);
  }

  public void setListener(@Nullable TabChangeListener listener) {
    myListener = listener;
  }

  public void changeTab(@NotNull String tabTitle) {
    myTabbedPane.setSelectedTitle(tabTitle);
  }

  @Override
  public void highlightOptions(@NotNull String searchString) {
    for (CodeStyleAbstractPanel tab : tabs) {
      tab.highlightOptions(searchString);
    }
  }
}
