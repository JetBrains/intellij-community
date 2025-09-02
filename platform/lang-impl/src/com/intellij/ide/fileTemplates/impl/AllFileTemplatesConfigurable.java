// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.impl;

import com.intellij.CommonBundle;
import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public final class AllFileTemplatesConfigurable implements SearchableConfigurable, Configurable.NoMargin, Configurable.NoScroll,
                                                           Configurable.VariableProjectAppLevel, Configurable.WithEpDependencies {
  private static final Logger LOG = Logger.getInstance(AllFileTemplatesConfigurable.class);
  
  static final class Provider extends ConfigurableProvider {
    private final Project myProject;

    Provider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public @NotNull Configurable createConfigurable() {
      return new AllFileTemplatesConfigurable(myProject);
    }
  }

  private final Project myProject;
  private final FileTemplateManager manager;
  private JPanel myMainPanel;
  private FileTemplateTab currentTab;
  private FileTemplateTab myTemplatesList;
  private FileTemplateTab myIncludesList;
  private FileTemplateTab myCodeTemplatesList;
  private @Nullable FileTemplateTab otherTemplatesList;
  private JComponent myToolBar;
  private TabbedPaneWrapper myTabbedPane;
  private FileTemplateConfigurable editor;
  private boolean isModified;
  private JComponent myEditorComponent;
  private JPanel myLeftPanel;
  private SchemesPanel mySchemesPanel;
  private FileTemplateTab[] myTabs;
  private Disposable myUIDisposable;
  private Set<String> internalTemplateNames;

  private FileTemplatesScheme myScheme;
  private final Map<FileTemplatesScheme, Map<String, List<FileTemplate>>> changesCache = new HashMap<>();

  private static final String CURRENT_TAB = "FileTemplates.CurrentTab";
  private static final String SELECTED_TEMPLATE = "FileTemplates.SelectedTemplate";

  public AllFileTemplatesConfigurable(Project project) {
    myProject = project;
    manager = FileTemplateManager.getInstance(project);
    myScheme = manager.getCurrentScheme();
  }

  private void onRemove() {
    currentTab.removeSelected();
    isModified = true;
  }

  private void onAdd(boolean child) {
    String ext = StringUtil.notNullize(JBIterable.from(IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages())
      .filterMap(Language::getAssociatedFileType)
      .filterMap(FileType::getDefaultExtension)
      .first(), "txt");
    FileTemplateBase selected = ObjectUtils.tryCast(getSelectedTemplate(), FileTemplateBase.class);
    if (selected == null && child) return;
    String name = child ? selected.getChildName(selected.getChildren().length) : IdeBundle.message("template.unnamed");
    FileTemplate template = createTemplate(name, ext, "", child);
    if (child) {
      selected.addChild(template);
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public @NotNull FileTemplate createTemplate(@NotNull String prefName, @NotNull String extension, @NotNull String content, boolean child) {
    List<FileTemplate> templates = currentTab.getTemplates();
    FileTemplate newTemplate = FileTemplateUtil.createTemplate(prefName, extension, content, templates);
    if (child) {
      int index = currentTab.getTemplates().indexOf(getSelectedTemplate());
      currentTab.insertTemplate(newTemplate, index + 1);
    }
    else {
      currentTab.addTemplate(newTemplate);
    }
    isModified = true;
    currentTab.selectTemplate(newTemplate);
    fireListChanged();
    editor.focusToNameField();
    return newTemplate;
  }

  private void onClone() {
    try {
      editor.apply();
    }
    catch (ConfigurationException ignore) {
    }

    FileTemplate selected = getSelectedTemplate();
    if (selected == null) {
      return;
    }

    List<FileTemplate> templates = currentTab.getTemplates();
    Set<String> names = new HashSet<>(templates.size());
    for (FileTemplate template : templates) {
      names.add(template.getName());
    }
    @SuppressWarnings("UnresolvedPropertyKey")
    final String nameTemplate = IdeBundle.message("template.copy.N.of.T");

    String name = FileTemplateBase.isChild(selected) && selected instanceof FileTemplateBase 
                  ? ((FileTemplateBase)selected).getChildName(selected.getChildren().length)
                  : MessageFormat.format(nameTemplate, "", selected.getName());
    int i = 0;
    while (names.contains(name)) {
      name = MessageFormat.format(nameTemplate, ++i + " ", selected.getName());
    }
    final FileTemplateBase newTemplate = new CustomFileTemplate(name, selected.getExtension());
    newTemplate.setText(selected.getText());
    newTemplate.setFileName(selected.getFileName());
    newTemplate.setReformatCode(selected.isReformatCode());
    newTemplate.setLiveTemplateEnabled(selected.isLiveTemplateEnabled());
    newTemplate.setChildren(ContainerUtil.map2Array(selected.getChildren(), FileTemplate.class, template -> template.clone()));
    newTemplate.updateChildrenNames();
    currentTab.addTemplate(newTemplate);
    for (FileTemplate child : newTemplate.getChildren()) {
      currentTab.addTemplate(child);
    }
    isModified = true;
    currentTab.selectTemplate(newTemplate);
    fireListChanged();
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.file.templates");
  }

  @Override
  public String getHelpTopic() {
    int index = myTabbedPane.getSelectedIndex();
    return switch (index) {
      case 0 -> "fileTemplates.templates";
      case 1 -> "fileTemplates.includes";
      case 2 -> "fileTemplates.code";
      case 3 -> "fileTemplates.j2ee";
      default -> throw new IllegalStateException("wrong index: " + index);
    };
  }

  @Override
  public JComponent createComponent() {
    internalTemplateNames = ContainerUtil.map2Set(manager.getInternalTemplates(), FileTemplate::getName);

    myUIDisposable = Disposer.newDisposable();

    myTemplatesList = new FileTemplateTabAsList(getTemplatesTitle()) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myIncludesList = new FileTemplateTabAsList(getIncludesTitle()) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myCodeTemplatesList = new FileTemplateTabAsList(getCodeTitle()) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    currentTab = myTemplatesList;

    final List<FileTemplateTab> allTabs = new ArrayList<>(Arrays.asList(myTemplatesList, myIncludesList, myCodeTemplatesList));

    final List<FileTemplateGroupDescriptorFactory> factories = FileTemplateGroupDescriptorFactory.EXTENSION_POINT_NAME.getExtensionList();
    if (!factories.isEmpty()) {
      otherTemplatesList = new FileTemplateTabAsTree(getOtherTitle()) {
        @Override
        public void onTemplateSelected() {
          onListSelectionChanged();
        }

        @Override
        protected FileTemplateNode initModel() {
          SortedSet<FileTemplateGroupDescriptor> categories =
            new TreeSet<>(Comparator.comparing(FileTemplateGroupDescriptor::getTitle));

          for (FileTemplateGroupDescriptorFactory templateGroupFactory : factories) {
            ContainerUtil.addIfNotNull(categories, templateGroupFactory.getFileTemplatesDescriptor());
          }

          return new FileTemplateNode("ROOT", null, ContainerUtil.map(categories, FileTemplateNode::new));
        }
      };
      allTabs.add(otherTemplatesList);
    }

    editor = new FileTemplateConfigurable(myProject);
    editor.addChangeListener(__ -> onEditorChanged());
    myEditorComponent = editor.createComponent();
    myEditorComponent.setBorder(JBUI.Borders.empty(10, 0, 10, 10));

    myTabs = allTabs.toArray(new FileTemplateTab[0]);
    myTabbedPane = new TabbedPaneWrapper(myUIDisposable);
    myTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    myLeftPanel = new JPanel(new CardLayout());
    for (FileTemplateTab tab : myTabs) {
      myLeftPanel.add(ScrollPaneFactory.createScrollPane(tab.getComponent()), tab.getTitle());
      JPanel fakePanel = new JPanel();
      fakePanel.setPreferredSize(new Dimension(0, 0));
      myTabbedPane.addTab(tab.getTitle(), fakePanel);
    }

    myTabbedPane.addChangeListener(__ -> onTabChanged());

    DefaultActionGroup group = new DefaultActionGroup();
    AnAction removeAction = new DumbAwareAction(IdeBundle.message("action.remove.template"), null, AllIcons.General.Remove) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onRemove();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        if (currentTab == null) {
          e.getPresentation().setEnabled(false);
          return;
        }
        FileTemplate selectedItem = getSelectedTemplate();
        e.getPresentation().setEnabled(selectedItem != null && !isInternalTemplate(selectedItem.getName(), currentTab.getTitle()));
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    AnAction addAction = new DumbAwareAction(IdeBundle.message("action.create.template"), null, AllIcons.General.Add) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onAdd(false);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!(currentTab == myCodeTemplatesList || currentTab == otherTemplatesList));
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    AnAction addChildAction = new DumbAwareAction(IdeBundle.message("action.create.child.template"), null, AllIcons.Actions.AddFile) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onAdd(true);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(getSelectedTemplate() != null &&
                                       currentTab != null &&
                                       !isInternalTemplate(getSelectedTemplate().getName(), currentTab.getTitle()) &&
                                       !FileTemplateBase.isChild(getSelectedTemplate()) &&
                                       currentTab == myTemplatesList);
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    AnAction cloneAction = new DumbAwareAction(IdeBundle.message("action.copy.template"), null, PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onClone();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(currentTab != myCodeTemplatesList
                                       && currentTab != otherTemplatesList
                                       && getSelectedTemplate() != null);
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    AnAction resetAction = new DumbAwareAction(IdeBundle.message("action.reset.to.default"), null, AllIcons.Actions.Rollback) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onReset();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        if (currentTab == null) {
          e.getPresentation().setEnabled(false);
          return;
        }
        final FileTemplate selectedItem = getSelectedTemplate();
        e.getPresentation().setEnabled(selectedItem instanceof BundledFileTemplate && !selectedItem.isDefault());
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    group.add(addAction);
    group.add(addChildAction);
    group.add(removeAction);
    group.add(cloneAction);
    group.add(resetAction);

    addAction.registerCustomShortcutSet(CommonShortcuts.getInsert(), currentTab.getComponent());
    removeAction.registerCustomShortcutSet(CommonShortcuts.getDelete(),
                                           currentTab.getComponent());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("FileTemplatesConfigurable", group, true);
    toolbar.setTargetComponent(myLeftPanel);
    myToolBar = toolbar.getComponent();
    myToolBar.setBorder(new CustomLineBorder(1, 1, 0, 1));

    mySchemesPanel = new SchemesPanel();
    mySchemesPanel.setBorder(JBUI.Borders.empty(5, 10, 0, 10));
    mySchemesPanel.resetSchemes(Arrays.asList(FileTemplatesScheme.DEFAULT, manager.getProjectScheme()));

    JPanel leftPanelWrapper = new JPanel(new BorderLayout());
    leftPanelWrapper.setBorder(JBUI.Borders.empty(10, 10, 10, 0));
    leftPanelWrapper.add(BorderLayout.NORTH, myToolBar);
    leftPanelWrapper.add(BorderLayout.CENTER, myLeftPanel);

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(myTabbedPane.getComponent(), BorderLayout.NORTH);
    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setDividerWidth(JBUIScale.scale(10));
    splitter.setFirstComponent(leftPanelWrapper);
    splitter.setSecondComponent(myEditorComponent);
    centerPanel.add(splitter, BorderLayout.CENTER);

    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(mySchemesPanel, BorderLayout.NORTH);
    myMainPanel.add(centerPanel, BorderLayout.CENTER);

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String tabName = propertiesComponent.getValue(CURRENT_TAB);
    selectTab(tabName);

    return myMainPanel;
  }

  private @Nullable FileTemplate getSelectedTemplate() {
    return currentTab.getSelectedTemplate();
  }

  private void onReset() {
    FileTemplate selected = getSelectedTemplate();
    if (selected instanceof BundledFileTemplate) {
      if (Messages.showOkCancelDialog(IdeBundle.message("prompt.reset.to.original.template"),
                                      IdeBundle.message("title.reset.template"), LangBundle.message("button.reset"), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()) !=
          Messages.OK) {
        return;
      }
      ((BundledFileTemplate)selected).revertToDefaults();
      editor.reset();
      isModified = true;
    }
  }

  private void onEditorChanged() {
    fireListChanged();
  }

  private void onTabChanged() {
    applyEditor(getSelectedTemplate());

    FileTemplateTab tab = currentTab;
    final int selectedIndex = myTabbedPane.getSelectedIndex();
    if (0 <= selectedIndex && selectedIndex < myTabs.length) {
      currentTab = myTabs[selectedIndex];
    }
    ((CardLayout)myLeftPanel.getLayout()).show(myLeftPanel, currentTab.getTitle());
    onListSelectionChanged();
    // request focus to a list (or tree) later to avoid moving focus to the tabbed pane
    if (tab != currentTab) EventQueue.invokeLater(currentTab.getComponent()::requestFocus);
  }

  private void onListSelectionChanged() {
    FileTemplate selectedValue = getSelectedTemplate();
    FileTemplate prevTemplate = editor == null ? null : editor.getTemplate();
    if (prevTemplate != selectedValue) {
      LOG.assertTrue(editor != null, "selected:" + selectedValue + "; prev:" + prevTemplate);
      // selection has changed
      if (prevTemplate != null && currentTab.getTemplates().contains(prevTemplate) && !applyEditor(prevTemplate)) {
        return;
      }
      if (selectedValue == null) {
        editor.setTemplate(null, FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultTemplateDescription());
        myEditorComponent.repaint();
      }
      else {
        selectTemplate(selectedValue);
      }
    }
  }

  private boolean applyEditor(FileTemplate prevTemplate) {
    if (editor.isModified()) {
      try {
        isModified = true;
        editor.apply();
        fireListChanged();
      }
      catch (ConfigurationException e) {
        if (currentTab.getTemplates().contains(prevTemplate)) {
          currentTab.selectTemplate(prevTemplate);
        }
        Messages.showErrorDialog(myMainPanel, e.getMessage(), IdeBundle.message("title.cannot.save.current.template"));
        return false;
      }
    }
    return true;
  }

  private void selectTemplate(FileTemplate template) {
    Supplier<String> defDesc = null;
    if (currentTab == myTemplatesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultTemplateDescription();
    }
    else if (currentTab == myIncludesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultIncludeDescription();
    }
    if (editor.getTemplate() != template) {
      final boolean isInternal = template != null && isInternalTemplate(template.getName(), currentTab.getTitle());
      editor.setTemplate(template, defDesc, isInternal);
      editor.setShowAdjustCheckBox(myTemplatesList == currentTab);
    }
  }

  @Override
  public boolean isProjectLevel() {
    return myScheme != null && myScheme != FileTemplatesScheme.DEFAULT && !myScheme.getProject().isDefault();
  }

  // internal template could not be removed
  private static boolean isInternalTemplate(String templateName, String templateTabTitle) {
    if (templateName == null) {
      return false;
    }
    if (Comparing.strEqual(templateTabTitle, getTemplatesTitle())) {
      return isInternalTemplateName(templateName);
    }
    if (Comparing.strEqual(templateTabTitle, getCodeTitle())) {
      return true;
    }
    if (Comparing.strEqual(templateTabTitle, getOtherTitle())) {
      return true;
    }
    if (Comparing.strEqual(templateTabTitle, getIncludesTitle())) {
      return Comparing.strEqual(templateName, FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    }
    return false;
  }

  private static boolean isInternalTemplateName(final String templateName) {
    for(InternalTemplateBean bean: InternalTemplateBean.EP_NAME.getExtensionList()) {
      if (Comparing.strEqual(templateName, bean.name)) {
        return true;
      }
    }
    return false;
  }

  private void initLists() {
    FileTemplatesScheme scheme = manager.getCurrentScheme();
    manager.setCurrentScheme(myScheme);
    myTemplatesList.init(getTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY));
    myIncludesList.init(getTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY));
    myCodeTemplatesList.init(getTemplates(FileTemplateManager.CODE_TEMPLATES_CATEGORY));
    myTabbedPane.setEnabledAt(2, !myCodeTemplatesList.templates.isEmpty());
    if (otherTemplatesList != null) {
      otherTemplatesList.init(getTemplates(FileTemplateManager.J2EE_TEMPLATES_CATEGORY));
    }
    manager.setCurrentScheme(scheme);
  }

  private List<FileTemplate> getTemplates(String category) {
    Map<String, List<FileTemplate>> templates = changesCache.get(myScheme);
    return templates == null ? List.of(manager.getTemplates(category)) : templates.get(category);
  }

  @Override
  public boolean isModified() {
    return myScheme != manager.getCurrentScheme() || !changesCache.isEmpty() || isSchemeModified();
  }

  private boolean isSchemeModified() {
    return isModified || editor != null && editor.isModified();
  }

  private void checkCanApply(FileTemplateTab list) throws ConfigurationException {
    List<FileTemplate> templates = currentTab.getTemplates();
    Set<String> allNames = new HashSet<>();
    FileTemplate itemWithError = null;
    String errorString = null;
    for (FileTemplate template : templates) {
      String currName = template.getName();
      if (currName.isEmpty()) {
        itemWithError = template;
        errorString = IdeBundle.message("error.please.specify.template.name");
        break;
      }
      if (!allNames.add(currName + "." + template.getExtension())) {
        itemWithError = template;
        errorString = LangBundle.message("dialog.message.template.with.name.already.exists", currName);
        break;
      }
    }

    if (itemWithError != null) {
      myTabbedPane.setSelectedIndex(Arrays.asList(myTabs).indexOf(list));
      selectTemplate(itemWithError);
      list.selectTemplate(itemWithError);
      ApplicationManager.getApplication().invokeLater(editor::focusToNameField);
      throw new ConfigurationException(errorString);
    }
  }

  private void fireListChanged() {
    if (currentTab != null) {
      currentTab.fireDataChanged();
    }
    if (myMainPanel != null) {
      myMainPanel.revalidate();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    if (editor != null && editor.isModified()) {
      isModified = true;
      editor.apply();
    }

    for (FileTemplateTab list : myTabs) {
      checkCanApply(list);
    }
    updateCache();
    for (Map.Entry<FileTemplatesScheme, Map<String, List<FileTemplate>>> entry : changesCache.entrySet()) {
      manager.setCurrentScheme(entry.getKey());
      Map<String, List<FileTemplate>> templates = entry.getValue();
      manager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, templates.get(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY));
      manager.setTemplates(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY,
                           templates.get(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY));
      manager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY,
                           templates.get(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY));
      manager.setTemplates(FileTemplateManager.CODE_TEMPLATES_CATEGORY, templates.get(FileTemplateManager.CODE_TEMPLATES_CATEGORY));
      manager.setTemplates(FileTemplateManager.J2EE_TEMPLATES_CATEGORY, templates.get(FileTemplateManager.J2EE_TEMPLATES_CATEGORY));
    }
    changesCache.clear();

    manager.setCurrentScheme(myScheme);

    if (editor != null) {
      isModified = false;
      fireListChanged();
    }
  }

  private void selectTab(String tabName) {
    int idx = 0;
    for (FileTemplateTab tab : myTabs) {
      if (Comparing.strEqual(tab.getTitle(), tabName)) {
        currentTab = tab;
        myTabbedPane.setSelectedIndex(idx);
        return;
      }
      idx++;
    }
  }

  @Override
  public void reset() {
    editor.reset();
    changeScheme(manager.getCurrentScheme());
    mySchemesPanel.selectScheme(myScheme);
    changesCache.clear();
    isModified = false;
  }

  @Override
  public void disposeUIResources() {
    if (currentTab != null) {
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      propertiesComponent.setValue(CURRENT_TAB, currentTab.getTitle(), getTemplatesTitle());
      final FileTemplate template = getSelectedTemplate();
      if (template != null) {
        propertiesComponent.setValue(SELECTED_TEMPLATE, template.getName());
      }
    }

    if (editor != null) {
      editor.disposeUIResources();
      editor = null;
      myEditorComponent = null;
    }
    myMainPanel = null;
    if (myUIDisposable != null) {
      Disposer.dispose(myUIDisposable);
      myUIDisposable = null;
    }
    myTabbedPane = null;
    myToolBar = null;
    myTabs = null;
    currentTab = null;
    myTemplatesList = null;
    myCodeTemplatesList = null;
    myIncludesList = null;
    otherTemplatesList = null;
  }

  @Override
  public @NotNull String getId() {
    return "fileTemplates";
  }

  public static void editCodeTemplate(final @NotNull String templateId, Project project) {
    final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
    final AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(project);
    util.editConfigurable(project, configurable, () -> {
      configurable.myTabbedPane.setSelectedIndex(ArrayUtil.indexOf(configurable.myTabs, configurable.myCodeTemplatesList));
      for (FileTemplate template : configurable.myCodeTemplatesList.getTemplates()) {
        if (Objects.equals(templateId, template.getName())) {
          configurable.myCodeTemplatesList.selectTemplate(template);
          break;
        }
      }
    });
  }

  public static void editOtherTemplate(@NotNull String templateFileName, Project project) {
    ShowSettingsUtil util = ShowSettingsUtil.getInstance();
    AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(project);
    util.editConfigurable(project, configurable, () -> {
      FileTemplateTab otherTemplatesList = configurable.otherTemplatesList;
      if (otherTemplatesList == null) return;

      configurable.myTabbedPane.setSelectedIndex(ArrayUtil.indexOf(configurable.myTabs, otherTemplatesList));
      for (FileTemplate template : otherTemplatesList.getTemplates()) {
        String fileName = template.getName();
        if (!template.getExtension().isEmpty()) {
          fileName += "." + template.getExtension();
        }

        if (Objects.equals(templateFileName, fileName)) {
          otherTemplatesList.selectTemplate(template);
          break;
        }
      }
    });
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void changeScheme(@NotNull FileTemplatesScheme scheme) {
    if (editor != null && editor.isModified()) {
      isModified = true;
      try {
        editor.apply();
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(myEditorComponent, e.getMessage(), e.getTitle());
        return;
      }
    }
    updateCache();
    myScheme = scheme;

    initLists();
  }

  private void updateCache() {
    if (!isSchemeModified() || changesCache.containsKey(myScheme) || internalTemplateNames == null) {
      return;
    }

    Map<String, List<FileTemplate>> templates = new HashMap<>();
    List<FileTemplate> allTemplates = myTemplatesList.getTemplates();
    templates.put(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY,
                  ContainerUtil.filter(allTemplates, it -> !internalTemplateNames.contains(it.getName())));
    templates.put(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY,
                  ContainerUtil.filter(allTemplates, it -> internalTemplateNames.contains(it.getName())));
    templates.put(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, myIncludesList.getTemplates());
    templates.put(FileTemplateManager.CODE_TEMPLATES_CATEGORY, myCodeTemplatesList.getTemplates());
    templates.put(FileTemplateManager.J2EE_TEMPLATES_CATEGORY,
                  otherTemplatesList == null ? Collections.emptyList() : otherTemplatesList.getTemplates());
    changesCache.put(myScheme, templates);
  }

  public @NotNull FileTemplateManager getManager() {
    return manager;
  }

  @TestOnly
  @ApiStatus.Internal
  public FileTemplateTab[] getTabs() {
    return myTabs;
  }

  private final class SchemesPanel extends SimpleSchemesPanel<FileTemplatesScheme> implements SchemesModel<FileTemplatesScheme> {
    @Override
    protected @NotNull AbstractSchemeActions<FileTemplatesScheme> createSchemeActions() {
      return new AbstractSchemeActions<>(this) {
        @Override
        protected void resetScheme(@NotNull FileTemplatesScheme scheme) {
          throw new UnsupportedOperationException();
        }

        @Override
        protected void duplicateScheme(@NotNull FileTemplatesScheme scheme, @NotNull String newName) {
          throw new UnsupportedOperationException();
        }

        @Override
        protected void onSchemeChanged(@Nullable FileTemplatesScheme scheme) {
          if (scheme != null) changeScheme(scheme);
        }

        @Override
        protected void renameScheme(@NotNull FileTemplatesScheme scheme, @NotNull String newName) {
          throw new UnsupportedOperationException();
        }

        @Override
        protected @NotNull Class<FileTemplatesScheme> getSchemeType() {
          return FileTemplatesScheme.class;
        }
      };
    }

    @Override
    public @NotNull SchemesModel<FileTemplatesScheme> getModel() {
      return this;
    }

    @Override
    protected boolean supportsProjectSchemes() {
      return false;
    }

    @Override
    protected boolean highlightNonDefaultSchemes() {
      return false;
    }

    @Override
    public boolean useBoldForNonRemovableSchemes() {
      return true;
    }

    @Override
    public boolean canDuplicateScheme(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public boolean canResetScheme(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public boolean canDeleteScheme(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public boolean isProjectScheme(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public boolean canRenameScheme(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public boolean containsScheme(@NotNull String name, boolean projectScheme) {
      return false;
    }

    @Override
    public boolean differsFromDefault(@NotNull FileTemplatesScheme scheme) {
      return false;
    }

    @Override
    public void removeScheme(@NotNull FileTemplatesScheme scheme) {
      throw new UnsupportedOperationException();
    }
  }

  private static @NlsContexts.TabTitle String getTemplatesTitle() {
    return IdeBundle.message("tab.filetemplates.templates");
  }

  private static @NlsContexts.TabTitle String getIncludesTitle() {
    return IdeBundle.message("tab.filetemplates.includes");
  }

  private static @NlsContexts.TabTitle String getCodeTitle() {
    return IdeBundle.message("tab.filetemplates.code");
  }

  private static @NlsContexts.TabTitle String getOtherTitle() {
    return IdeBundle.message("tab.filetemplates.j2ee");
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(InternalTemplateBean.EP_NAME);
  }
}
