/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

import static com.intellij.ide.fileTemplates.FileTemplateManager.*;

/*
 * @author: MYakovlev
 * Date: Jul 26, 2002
 * Time: 12:44:56 PM
 */

public class AllFileTemplatesConfigurable implements SearchableConfigurable, Configurable.NoMargin, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable");

  private static final String TEMPLATES_TITLE = IdeBundle.message("tab.filetemplates.templates");
  private static final String INCLUDES_TITLE = IdeBundle.message("tab.filetemplates.includes");
  private static final String CODE_TITLE = IdeBundle.message("tab.filetemplates.code");
  private static final String OTHER_TITLE = IdeBundle.message("tab.filetemplates.j2ee");

  private final Project myProject;
  private final FileTemplateManager myManager;
  private JPanel myMainPanel;
  private FileTemplateTab myCurrentTab;
  private FileTemplateTab myTemplatesList;
  private FileTemplateTab myIncludesList;
  private FileTemplateTab myCodeTemplatesList;
  @Nullable
  private FileTemplateTab myOtherTemplatesList;
  private JComponent myToolBar;
  private TabbedPaneWrapper myTabbedPane;
  private FileTemplateConfigurable myEditor;
  private boolean myModified = false;
  private JComponent myEditorComponent;
  private JPanel myLeftPanel;
  private FileTemplateTab[] myTabs;
  private Disposable myUIDisposable;
  private final Set<String> myInternalTemplateNames;

  private FileTemplatesScheme myScheme;
  private final Map<FileTemplatesScheme, Map<String, FileTemplate[]>> myChangesCache =
    new HashMap<>();

  private static final String CURRENT_TAB = "FileTemplates.CurrentTab";
  private static final String SELECTED_TEMPLATE = "FileTemplates.SelectedTemplate";

  public AllFileTemplatesConfigurable(Project project) {
    myProject = project;
    myManager = getInstance(project);
    myScheme = myManager.getCurrentScheme();
    myInternalTemplateNames = ContainerUtil.map2Set(myManager.getInternalTemplates(), template -> template.getName());
  }

  private void onRemove() {
    myCurrentTab.removeSelected();
    myModified = true;
  }

  private void onAdd() {
    String ext = "java";
    final FileTemplateDefaultExtension[] defaultExtensions = Extensions.getExtensions(FileTemplateDefaultExtension.EP_NAME);
    if (defaultExtensions.length > 0) {
      ext = defaultExtensions[0].value;
    }
    createTemplate(IdeBundle.message("template.unnamed"), ext, "");
  }

  private FileTemplate createTemplate(final @NotNull String prefName, final @NotNull String extension, final @NotNull String content) {
    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final FileTemplate newTemplate = FileTemplateUtil.createTemplate(prefName, extension, content, templates);
    myCurrentTab.addTemplate(newTemplate);
    myModified = true;
    myCurrentTab.selectTemplate(newTemplate);
    fireListChanged();
    myEditor.focusToNameField();
    return newTemplate;
  }

  private void onClone() {
    try {
      myEditor.apply();
    }
    catch (ConfigurationException ignore) {
    }
    
    final FileTemplate selected = myCurrentTab.getSelectedTemplate();
    if (selected == null) {
      return;
    }

    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final Set<String> names = new HashSet<>();
    for (FileTemplate template : templates) {
      names.add(template.getName());
    }
    @SuppressWarnings({"UnresolvedPropertyKey"})
    final String nameTemplate = IdeBundle.message("template.copy.N.of.T");
    String name = MessageFormat.format(nameTemplate, "", selected.getName());
    int i = 0;
    while (names.contains(name)) {
      name = MessageFormat.format(nameTemplate, ++i + " ", selected.getName());
    }
    final FileTemplate newTemplate = new CustomFileTemplate(name, selected.getExtension());
    newTemplate.setText(selected.getText());
    newTemplate.setReformatCode(selected.isReformatCode());
    newTemplate.setLiveTemplateEnabled(selected.isLiveTemplateEnabled());
    myCurrentTab.addTemplate(newTemplate);
    myModified = true;
    myCurrentTab.selectTemplate(newTemplate);
    fireListChanged();
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.file.templates");
  }

  @Override
  public String getHelpTopic() {
    int index = myTabbedPane.getSelectedIndex();
    switch (index) {
      case 0:
        return "fileTemplates.templates";
      case 1:
        return "fileTemplates.includes";
      case 2:
        return "fileTemplates.code";
      case 3:
        return "fileTemplates.j2ee";
      default:
        throw new IllegalStateException("wrong index: " + index);
    }
  }

  @Override
  public JComponent createComponent() {
    myUIDisposable = Disposer.newDisposable();

    myTemplatesList = new FileTemplateTabAsList(TEMPLATES_TITLE) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myIncludesList = new FileTemplateTabAsList(INCLUDES_TITLE) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myCodeTemplatesList = new FileTemplateTabAsList(CODE_TITLE) {
      @Override
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myCurrentTab = myTemplatesList;

    final List<FileTemplateTab> allTabs = new ArrayList<>(Arrays.asList(myTemplatesList, myIncludesList, myCodeTemplatesList));

    final FileTemplateGroupDescriptorFactory[] factories = Extensions.getExtensions(FileTemplateGroupDescriptorFactory.EXTENSION_POINT_NAME);
    if (factories.length != 0) {
      myOtherTemplatesList = new FileTemplateTabAsTree(OTHER_TITLE) {
        @Override
        public void onTemplateSelected() {
          onListSelectionChanged();
        }

        @Override
        protected FileTemplateNode initModel() {
          SortedSet<FileTemplateGroupDescriptor> categories =
            new TreeSet<>((o1, o2) -> o1.getTitle().compareTo(o2.getTitle()));


          for (FileTemplateGroupDescriptorFactory templateGroupFactory : factories) {
            ContainerUtil.addIfNotNull(categories, templateGroupFactory.getFileTemplatesDescriptor());
          }

          //noinspection HardCodedStringLiteral
          return new FileTemplateNode("ROOT", null,
                                      ContainerUtil.map2List(categories, s -> new FileTemplateNode(s)));
        }
      };
      allTabs.add(myOtherTemplatesList);
    }

    myEditor = new FileTemplateConfigurable(myProject);
    myEditor.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        onEditorChanged();
      }
    });
    myEditorComponent = myEditor.createComponent();
    myEditorComponent.setBorder(JBUI.Borders.empty(10, 0, 10, 10));

    myTabs = allTabs.toArray(new FileTemplateTab[allTabs.size()]);
    myTabbedPane = new TabbedPaneWrapper(myUIDisposable);
    myTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    myLeftPanel = new JPanel(new CardLayout());
    myLeftPanel.setBorder(JBUI.Borders.empty(10, 10, 10, 0));
    for (FileTemplateTab tab : myTabs) {
      myLeftPanel.add(ScrollPaneFactory.createScrollPane(tab.getComponent()), tab.getTitle());
      JPanel fakePanel = new JPanel();
      fakePanel.setPreferredSize(new Dimension(0, 0));
      myTabbedPane.addTab(tab.getTitle(), fakePanel);
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        onTabChanged();
      }
    });

    DefaultActionGroup group = new DefaultActionGroup();
    AnAction removeAction = new AnAction(IdeBundle.message("action.remove.template"), null, AllIcons.General.Remove) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        onRemove();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        FileTemplate selectedItem = myCurrentTab.getSelectedTemplate();
        e.getPresentation().setEnabled(selectedItem != null && !isInternalTemplate(selectedItem.getName(), myCurrentTab.getTitle()));
      }
    };
    AnAction addAction = new AnAction(IdeBundle.message("action.create.template"), null, AllIcons.General.Add) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        onAdd();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(!(myCurrentTab == myCodeTemplatesList || myCurrentTab == myOtherTemplatesList));
      }
    };
    AnAction cloneAction = new AnAction(IdeBundle.message("action.copy.template"), null, PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        onClone();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myCurrentTab != myCodeTemplatesList
                                       && myCurrentTab != myOtherTemplatesList
                                       && myCurrentTab.getSelectedTemplate() != null);
      }
    };
    AnAction resetAction = new AnAction(IdeBundle.message("action.reset.to.default"), null, AllIcons.Actions.Reset) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        onReset();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        final FileTemplate selectedItem = myCurrentTab.getSelectedTemplate();
        e.getPresentation().setEnabled(selectedItem instanceof BundledFileTemplate && !selectedItem.isDefault());
      }
    };
    group.add(addAction);
    group.add(removeAction);
    group.add(cloneAction);
    group.add(resetAction);

    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myCurrentTab.getComponent());
    removeAction.registerCustomShortcutSet(CommonShortcuts.getDelete(),
                                           myCurrentTab.getComponent());

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    myToolBar.setBorder(IdeBorderFactory.createEmptyBorder());

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(myToolBar, BorderLayout.WEST);
    JComponent schemaComponent =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultCompactActionGroup(new ChangeSchemaCombo(this)), true)
        .getComponent();
    JPanel schemaPanel = new JPanel(new BorderLayout());
    schemaPanel.add(schemaComponent, BorderLayout.EAST);
    schemaPanel.add(new JLabel("Schema:"), BorderLayout.WEST);
    toolbarPanel.add(schemaPanel, BorderLayout.EAST);


    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(myTabbedPane.getComponent(), BorderLayout.NORTH);
    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setDividerWidth(JBUI.scale(10));
    splitter.setFirstComponent(myLeftPanel);
    splitter.setSecondComponent(myEditorComponent);
    centerPanel.add(splitter, BorderLayout.CENTER);

    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(toolbarPanel, BorderLayout.NORTH);
    myMainPanel.add(centerPanel, BorderLayout.CENTER);

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String tabName = propertiesComponent.getValue(CURRENT_TAB);
    if (selectTab(tabName)) {
      //final String selectedTemplateName = propertiesComponent.getValue(SELECTED_TEMPLATE);
      //for (FileTemplate template : myCurrentTab.getTemplates()) {
      //  if (Comparing.strEqual(template.getName(), selectedTemplateName)) {
      //    myCurrentTab.selectTemplate(template);
      //    break;
      //  }
      //}
    }

    return myMainPanel;
  }

  private void onReset() {
    FileTemplate selected = myCurrentTab.getSelectedTemplate();
    if (selected instanceof BundledFileTemplate) {
      if (Messages.showOkCancelDialog(IdeBundle.message("prompt.reset.to.original.template"),
                                      IdeBundle.message("title.reset.template"), Messages.getQuestionIcon()) !=
          Messages.OK) {
        return;
      }
      ((BundledFileTemplate)selected).revertToDefaults();
      myEditor.reset();
      myModified = true;
    }
  }

  private void onEditorChanged() {
    fireListChanged();
  }

  private void onTabChanged() {
    applyEditor(myCurrentTab.getSelectedTemplate());
    
    final int selectedIndex = myTabbedPane.getSelectedIndex();
    if (0 <= selectedIndex && selectedIndex < myTabs.length) {
      myCurrentTab = myTabs[selectedIndex];
    }
    ((CardLayout)myLeftPanel.getLayout()).show(myLeftPanel, myCurrentTab.getTitle());
    onListSelectionChanged();
  }

  private void onListSelectionChanged() {
    FileTemplate selectedValue = myCurrentTab.getSelectedTemplate();
    FileTemplate prevTemplate = myEditor == null ? null : myEditor.getTemplate();
    if (prevTemplate != selectedValue) {
      LOG.assertTrue(myEditor != null, "selected:" + selectedValue + "; prev:" + prevTemplate);
      //selection has changed
      if (Arrays.asList(myCurrentTab.getTemplates()).contains(prevTemplate) && !applyEditor(prevTemplate)) {
        return;
      }
      if (selectedValue == null) {
        myEditor.setTemplate(null, FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultTemplateDescription());
        myEditorComponent.repaint();
      }
      else {
        selectTemplate(selectedValue);
      }
    }
  }

  private boolean applyEditor(FileTemplate prevTemplate) {
    if (myEditor.isModified()) {
      try {
        myModified = true;
        myEditor.apply();
        fireListChanged();
      }
      catch (ConfigurationException e) {
        if (Arrays.asList(myCurrentTab.getTemplates()).contains(prevTemplate)) {
          myCurrentTab.selectTemplate(prevTemplate);
        }
        Messages.showErrorDialog(myMainPanel, e.getMessage(), IdeBundle.message("title.cannot.save.current.template"));
        return false;
      }
    }
    return true;
  }

  private void selectTemplate(FileTemplate template) {
    URL defDesc = null;
    if (myCurrentTab == myTemplatesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultTemplateDescription();
    }
    else if (myCurrentTab == myIncludesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl(myProject).getDefaultIncludeDescription();
    }
    if (myEditor.getTemplate() != template) {
      myEditor.setTemplate(template, defDesc);
      final boolean isInternal = template != null && isInternalTemplate(template.getName(), myCurrentTab.getTitle());
      myEditor.setShowInternalMessage(isInternal ? " " : null);
      myEditor.setShowAdjustCheckBox(myTemplatesList == myCurrentTab);
    }
  }

  // internal template could not be removed and should be rendered bold
  public static boolean isInternalTemplate(String templateName, String templateTabTitle) {
    if (templateName == null) {
      return false;
    }
    if (Comparing.strEqual(templateTabTitle, TEMPLATES_TITLE)) {
      return isInternalTemplateName(templateName);
    }
    if (Comparing.strEqual(templateTabTitle, CODE_TITLE)) {
      return true;
    }
    if (Comparing.strEqual(templateTabTitle, OTHER_TITLE)) {
      return true;
    }
    if (Comparing.strEqual(templateTabTitle, INCLUDES_TITLE)) {
      return Comparing.strEqual(templateName, FILE_HEADER_TEMPLATE_NAME);
    }
    return false;
  }

  private static boolean isInternalTemplateName(final String templateName) {
    for(InternalTemplateBean bean: Extensions.getExtensions(InternalTemplateBean.EP_NAME)) {
      if (Comparing.strEqual(templateName, bean.name)) {
        return true;
      }
    }
    return false;
  }

  private void initLists() {
    FileTemplatesScheme scheme = myManager.getCurrentScheme();
    myManager.setCurrentScheme(myScheme);
    myTemplatesList.init(getTemplates(DEFAULT_TEMPLATES_CATEGORY));
    myIncludesList.init(getTemplates(INCLUDES_TEMPLATES_CATEGORY));
    myCodeTemplatesList.init(getTemplates(CODE_TEMPLATES_CATEGORY));
    if (myOtherTemplatesList != null) {
      myOtherTemplatesList.init(getTemplates(J2EE_TEMPLATES_CATEGORY));
    }
    myManager.setCurrentScheme(scheme);
  }

  private FileTemplate[] getTemplates(String category) {
    Map<String, FileTemplate[]> templates = myChangesCache.get(myScheme);
    if (templates == null) {
      return myManager.getTemplates(category);
    }
    else {
      return templates.get(category);
    }
  }

  @Override
  public boolean isModified() {
    return myScheme != myManager.getCurrentScheme() || !myChangesCache.isEmpty() || isSchemeModified();
  }

  private boolean isSchemeModified() {
    return myModified || myEditor != null && myEditor.isModified();
  }

  private void checkCanApply(FileTemplateTab list) throws ConfigurationException {
    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final List<String> allNames = new ArrayList<>();
    FileTemplate itemWithError = null;
    boolean errorInName = true;
    String errorString = null;
    for (FileTemplate template : templates) {
      final String currName = template.getName();
      if (currName.length() == 0) {
        itemWithError = template;
        errorString = IdeBundle.message("error.please.specify.template.name");
        break;
      }
      if (allNames.contains(currName)) {
        itemWithError = template;
        errorString = "Template with name \'" + currName + "\' already exists. Please specify a different template name";
        break;
      }
      allNames.add(currName);
    }

    if (itemWithError != null) {
      final boolean _errorInName = errorInName;
      myTabbedPane.setSelectedIndex(Arrays.asList(myTabs).indexOf(list));
      selectTemplate(itemWithError);
      list.selectTemplate(itemWithError);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (_errorInName) {
          myEditor.focusToNameField();
        }
        else {
          myEditor.focusToExtensionField();
        }
      });
      throw new ConfigurationException(errorString);
    }
  }

  private void fireListChanged() {
    if (myCurrentTab != null) {
      myCurrentTab.fireDataChanged();
    }
    if (myMainPanel != null) {
      myMainPanel.revalidate();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myEditor != null && myEditor.isModified()) {
      myModified = true;
      myEditor.apply();
    }

    for (FileTemplateTab list : myTabs) {
      checkCanApply(list);
    }
    updateCache();
    for (Map.Entry<FileTemplatesScheme, Map<String, FileTemplate[]>> entry : myChangesCache.entrySet()) {
      myManager.setCurrentScheme(entry.getKey());
      myManager.setTemplates(DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(entry.getValue().get(DEFAULT_TEMPLATES_CATEGORY)));
      myManager.setTemplates(INTERNAL_TEMPLATES_CATEGORY, Arrays.asList(entry.getValue().get(INTERNAL_TEMPLATES_CATEGORY)));
      myManager.setTemplates(INCLUDES_TEMPLATES_CATEGORY, Arrays.asList(entry.getValue().get(INCLUDES_TEMPLATES_CATEGORY)));
      myManager.setTemplates(CODE_TEMPLATES_CATEGORY, Arrays.asList(entry.getValue().get(CODE_TEMPLATES_CATEGORY)));
      myManager.setTemplates(J2EE_TEMPLATES_CATEGORY, Arrays.asList(entry.getValue().get(J2EE_TEMPLATES_CATEGORY)));
    }
    myChangesCache.clear();

    myManager.setCurrentScheme(myScheme);

    if (myEditor != null) {
      myModified = false;
      fireListChanged();
    }
  }

  public void selectTemplatesTab() {
    selectTab(TEMPLATES_TITLE);
  }
  
  private boolean selectTab(String tabName) {
    int idx = 0;
    for (FileTemplateTab tab : myTabs) {
      if (Comparing.strEqual(tab.getTitle(), tabName)) {
        myCurrentTab = tab;
        myTabbedPane.setSelectedIndex(idx);
        return true;
      }
      idx++;
    }
    return false;
  }

  @Override
  public void reset() {
    myEditor.reset();
    changeScheme(myManager.getCurrentScheme());
    myChangesCache.clear();
    myModified = false;
  }

  @Override
  public void disposeUIResources() {
    if (myCurrentTab != null) {
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      propertiesComponent.setValue(CURRENT_TAB, myCurrentTab.getTitle(), TEMPLATES_TITLE);
      final FileTemplate template = myCurrentTab.getSelectedTemplate();
      if (template != null) {
        propertiesComponent.setValue(SELECTED_TEMPLATE, template.getName());
      }
    }

    if (myEditor != null) {
      myEditor.disposeUIResources();
      myEditor = null;
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
    myCurrentTab = null;
    myTemplatesList = null;
    myCodeTemplatesList = null;
    myIncludesList = null;
    myOtherTemplatesList = null;
  }

  public FileTemplate createNewTemplate(@NotNull String preferredName, @NotNull String extension, @NotNull String text) {
    return createTemplate(preferredName, extension, text);
  }

  @Override
  @NotNull
  public String getId() {
    return "fileTemplates";
  }

  public static void editCodeTemplate(@NotNull final String templateId, Project project) {
    final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
    final AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(project);
    util.editConfigurable(project, configurable, () -> {
      configurable.myTabbedPane.setSelectedIndex(ArrayUtil.indexOf(configurable.myTabs, configurable.myCodeTemplatesList));
      for (FileTemplate template : configurable.myCodeTemplatesList.getTemplates()) {
        if (Comparing.equal(templateId, template.getName())) {
          configurable.myCodeTemplatesList.selectTemplate(template);
          break;
        }
      }
    });
  }

  public void changeScheme(FileTemplatesScheme scheme) {
    if (myEditor != null && myEditor.isModified()) {
      myModified = true;
      try {
        myEditor.apply();
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

  @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
  private void updateCache() {
    if (isSchemeModified()) {
      if (!myChangesCache.containsKey(myScheme)) {
        Map<String, FileTemplate[]> templates = new HashMap<>();
        FileTemplate[] allTemplates = myTemplatesList.getTemplates();
        templates.put(DEFAULT_TEMPLATES_CATEGORY, ContainerUtil.filter(allTemplates,
                                                                       template -> !myInternalTemplateNames.contains(template.getName())).toArray(FileTemplate.EMPTY_ARRAY));
        templates.put(INTERNAL_TEMPLATES_CATEGORY, ContainerUtil.filter(allTemplates,
                                                                        template -> myInternalTemplateNames.contains(template.getName())).toArray(FileTemplate.EMPTY_ARRAY));
        templates.put(INCLUDES_TEMPLATES_CATEGORY, myIncludesList.getTemplates());
        templates.put(CODE_TEMPLATES_CATEGORY, myCodeTemplatesList.getTemplates());
        templates.put(J2EE_TEMPLATES_CATEGORY, myOtherTemplatesList == null ? FileTemplate.EMPTY_ARRAY : myOtherTemplatesList.getTemplates());
        myChangesCache.put(myScheme, templates);
      }
    }
  }

  public FileTemplateManager getManager() {
    return myManager;
  }

  public FileTemplatesScheme getCurrentScheme() {
    return myScheme;
  }

  @TestOnly
  FileTemplateConfigurable getEditor() {
    return myEditor;
  }

  @TestOnly
  FileTemplateTab[] getTabs() {
    return myTabs;
  }
}
