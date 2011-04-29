/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/*
 * @author: MYakovlev
 * Date: Jul 26, 2002
 * Time: 12:44:56 PM
 */

public class AllFileTemplatesConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable");

  private static final String TEMPLATES_TITLE = IdeBundle.message("tab.filetemplates.templates");
  private static final String INCLUDES_TITLE = IdeBundle.message("tab.filetemplates.includes");
  private static final String CODE_TITLE = IdeBundle.message("tab.filetemplates.code");
  private static final String J2EE_TITLE = IdeBundle.message("tab.filetemplates.j2ee");
  private static final Icon ourIcon = IconLoader.getIcon("/general/fileTemplates.png");

  private JPanel myMainPanel;
  private FileTemplateTab myCurrentTab;
  private FileTemplateTab myTemplatesList;
  private FileTemplateTab myIncludesList;
  private FileTemplateTab myCodeTemplatesList;
  private FileTemplateTab myJ2eeTemplatesList;
  private JComponent myToolBar;
  private TabbedPaneWrapper myTabbedPane;
  private FileTemplateConfigurable myEditor;
  private boolean myModified = false;
  private JComponent myEditorComponent;
  private FileTemplateTab[] myTabs;
  private Disposable myUIDisposable;
  private final Set<String> myInternalTemplateNames = new HashSet<String>();

  private static final String CURRENT_TAB = "FileTemplates.CurrentTab";
  private static final String SELECTED_TEMPLATE = "FileTemplates.SelectedTemplate";

  public Icon getIcon() {
    return ourIcon;
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

  private void createTemplate(final @NotNull String prefName, final @NotNull String extension, final @NotNull String content) {
    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final Set<String> names = new HashSet<String>();
    for (FileTemplate template : templates) {
      names.add(template.getName());
    }
    String name = prefName;
    int i = 0;
    while (names.contains(name)) {
      name = prefName + " (" + ++i + ")";
    }
    final FileTemplate newTemplate = new CustomFileTemplate(name, extension);
    newTemplate.setText(content);
    myCurrentTab.addTemplate(newTemplate);
    myModified = true;
    myCurrentTab.selectTemplate(newTemplate);
    fireListChanged();
    myEditor.focusToNameField();
  }

  private void onClone() {
    final FileTemplate selected = myCurrentTab.getSelectedTemplate();
    if (selected == null) {
      return;
    }

    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final Set<String> names = new HashSet<String>();
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
    myCurrentTab.addTemplate(newTemplate);
    myModified = true;
    myCurrentTab.selectTemplate(newTemplate);
    fireListChanged();
  }

  public String getDisplayName() {
    return IdeBundle.message("title.file.templates");
  }

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

  public JComponent createComponent() {
    myUIDisposable = Disposer.newDisposable();
    myTemplatesList = new FileTemplateTabAsList(TEMPLATES_TITLE) {
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myIncludesList = new FileTemplateTabAsList(INCLUDES_TITLE) {
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myCodeTemplatesList = new FileTemplateTabAsList(CODE_TITLE) {
      public void onTemplateSelected() {
        onListSelectionChanged();
      }
    };
    myCurrentTab = myTemplatesList;

    final List<FileTemplateTab> allTabs = new ArrayList<FileTemplateTab>(Arrays.asList(myTemplatesList, myIncludesList, myCodeTemplatesList));

    final Set<FileTemplateGroupDescriptorFactory> factories = new THashSet<FileTemplateGroupDescriptorFactory>();
    ContainerUtil.addAll(factories, ApplicationManager.getApplication().getComponents(FileTemplateGroupDescriptorFactory.class));
    ContainerUtil.addAll(factories, Extensions.getExtensions(FileTemplateGroupDescriptorFactory.EXTENSION_POINT_NAME));

    if (!factories.isEmpty()) {
      myJ2eeTemplatesList = new FileTemplateTabAsTree(J2EE_TITLE) {
        public void onTemplateSelected() {
          onListSelectionChanged();
        }

        protected FileTemplateNode initModel() {
          SortedSet<FileTemplateGroupDescriptor> categories =
            new TreeSet<FileTemplateGroupDescriptor>(new Comparator<FileTemplateGroupDescriptor>() {
              public int compare(FileTemplateGroupDescriptor o1, FileTemplateGroupDescriptor o2) {
                return o1.getTitle().compareTo(o2.getTitle());
              }
            });


          for (FileTemplateGroupDescriptorFactory templateGroupFactory : factories) {
            ContainerUtil.addIfNotNull(templateGroupFactory.getFileTemplatesDescriptor(), categories);
          }

          //noinspection HardCodedStringLiteral
          return new FileTemplateNode("ROOT", null,
                                      ContainerUtil.map2List(categories, new Function<FileTemplateGroupDescriptor, FileTemplateNode>() {
                                        public FileTemplateNode fun(FileTemplateGroupDescriptor s) {
                                          return new FileTemplateNode(s);
                                        }
                                      }));
        }
      };
      allTabs.add(myJ2eeTemplatesList);
    }
    myTabs = allTabs.toArray(new FileTemplateTab[allTabs.size()]);
    myTabbedPane = new TabbedPaneWrapper(myUIDisposable);
    myTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    for (FileTemplateTab tab : myTabs) {
      myTabbedPane.addTab(tab.getTitle(), ScrollPaneFactory.createScrollPane(tab.getComponent()));
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        onTabChanged();
      }
    });

    DefaultActionGroup group = new DefaultActionGroup();
    AnAction removeAction = new AnAction(IdeBundle.message("action.remove.template"), null, IconLoader.getIcon("/general/remove.png")) {
      public void actionPerformed(AnActionEvent e) {
        onRemove();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        FileTemplate selectedItem = myCurrentTab.getSelectedTemplate();
        e.getPresentation().setEnabled(selectedItem != null && !isInternalTemplate(selectedItem.getName(), myCurrentTab.getTitle()));
      }
    };
    AnAction addAction = new AnAction(IdeBundle.message("action.create.template"), null, IconLoader.getIcon("/general/add.png")) {
      public void actionPerformed(AnActionEvent e) {
        onAdd();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(!(myCurrentTab == myCodeTemplatesList || myCurrentTab == myJ2eeTemplatesList));
      }
    };
    AnAction cloneAction = new AnAction(IdeBundle.message("action.copy.template"), null, IconLoader.getIcon("/actions/copy.png")) {
      public void actionPerformed(AnActionEvent e) {
        onClone();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myCurrentTab != myCodeTemplatesList
                                       && myCurrentTab != myJ2eeTemplatesList
                                       && myCurrentTab.getSelectedTemplate() != null);
      }
    };
    AnAction resetAction = new AnAction(IdeBundle.message("action.reset.to.default"), null, IconLoader.getIcon("/actions/reset.png")) {
      public void actionPerformed(AnActionEvent e) {
        onReset();
      }

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
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE,
                                           myCurrentTab.getComponent());

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();

    myEditor = new FileTemplateConfigurable();

    myEditor.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        onEditorChanged();
      }
    });
    myMainPanel = new JPanel(new GridBagLayout()) {
      public void doLayout() {
        doMainPanelLayout();
      }
    };
    // Layout manager is ignored
    myMainPanel.add(myToolBar,
                    new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                           GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2), 0, 0));
    myMainPanel.add(myTabbedPane.getComponent(),
                    new GridBagConstraints(0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                           new Insets(2, 2, 2, 2), 0, 0));
    myEditorComponent = myEditor.createComponent();
    myMainPanel.add(myEditorComponent,
                    new GridBagConstraints(1, 0, 1, 2, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(2, 2, 2, 2), 0, 0));

    myMainPanel.setMinimumSize(new Dimension(400, 300));
    myMainPanel.setPreferredSize(new Dimension(700, 500));

    return myMainPanel;
  }

  private void onReset() {
    FileTemplate selected = myCurrentTab.getSelectedTemplate();
    if (selected instanceof BundledFileTemplate) {
      if (Messages.showOkCancelDialog(IdeBundle.message("prompt.reset.to.original.template"),
                                      IdeBundle.message("title.reset.template"), Messages.getQuestionIcon()) !=
          DialogWrapper.OK_EXIT_CODE) {
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
    final int selectedIndex = myTabbedPane.getSelectedIndex();
    if (0 <= selectedIndex && selectedIndex < myTabs.length) {
      myCurrentTab = myTabs[selectedIndex];
    }
    onListSelectionChanged();
  }

  private void onListSelectionChanged() {
    FileTemplate selectedValue = myCurrentTab.getSelectedTemplate();
    FileTemplate prevTemplate = myEditor == null ? null : myEditor.getTemplate();
    if (prevTemplate != selectedValue) {
      LOG.assertTrue(myEditor != null, "selected:" + selectedValue + "; prev:" + prevTemplate);
      //selection has changed
      if (myEditor.isModified()) {
        try {
          myModified = true;
          myEditor.apply();
          fireListChanged();
        }
        catch (ConfigurationException e) {
          myCurrentTab.selectTemplate(prevTemplate);
          Messages.showErrorDialog(myMainPanel, e.getMessage(), IdeBundle.message("title.cannot.save.current.template"));
          return;
        }
      }
      if (selectedValue == null) {
        myEditor.setTemplate(null, FileTemplateManagerImpl.getInstanceImpl().getDefaultTemplateDescription());
        myEditorComponent.repaint();
      }
      else {
        selectTemplate(selectedValue);
      }
    }
  }

  private void selectTemplate(FileTemplate template) {
    URL defDesc = null;
    if (myCurrentTab == myTemplatesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl().getDefaultTemplateDescription();
    }
    else if (myCurrentTab == myIncludesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl().getDefaultIncludeDescription();
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
    if (Comparing.strEqual(templateTabTitle, J2EE_TITLE)) {
      return true;
    }
    if (Comparing.strEqual(templateTabTitle, INCLUDES_TITLE)) {
      return Comparing.strEqual(templateName, FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
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

  private void doMainPanelLayout() {
    Dimension toolbarPreferredSize = myToolBar.getPreferredSize();
    Dimension mainPanelSize = myMainPanel.getSize();
    Dimension scrollPanePreferedSize = myTabbedPane.getComponent().getPreferredSize();
    if (mainPanelSize.width < 1 || mainPanelSize.height < 1) {
      return;
    }
    int leftWidth = scrollPanePreferedSize.width;
    leftWidth = Math.min(leftWidth, mainPanelSize.width / 5);
    leftWidth = Math.max(leftWidth, 300); //to prevent tabs from scrolling
    //todo[myakovlev] Calculate tabs preferred size
    leftWidth = Math.max(leftWidth, toolbarPreferredSize.width);
    int x = 2;
    int y = 2;
    int width = toolbarPreferredSize.width;
    int height = toolbarPreferredSize.height;
    myToolBar.setBounds(x, y, width, height);
    y += height + 2;
    width = leftWidth + 2;
    height = Math.max(1, mainPanelSize.height - 2 - y);
    myTabbedPane.getComponent().setBounds(x, y, width, height);
    x += width + 4;
    y = 2;
    width = Math.max(1, mainPanelSize.width - 2 - x);
    height = Math.max(1, mainPanelSize.height - 2 - y);
    myEditorComponent.setBounds(x, y, width, height);
    myEditorComponent.revalidate();
  }

  private void initLists() {
    final FileTemplateManager templateManager = FileTemplateManager.getInstance();
    
    final FileTemplate[] internalTemplates = templateManager.getInternalTemplates();
    myInternalTemplateNames.clear();
    for (FileTemplate internalTemplate : internalTemplates) {
      myInternalTemplateNames.add(((FileTemplateBase)internalTemplate).getQualifiedName());
    }
    
    myTemplatesList.init(ArrayUtil.mergeArrays(internalTemplates, templateManager.getAllTemplates(), FileTemplate.class));
    myIncludesList.init(templateManager.getAllPatterns());
    myCodeTemplatesList.init(templateManager.getAllCodeTemplates());
    if (myJ2eeTemplatesList != null) {
      myJ2eeTemplatesList.init(templateManager.getAllJ2eeTemplates());
    }
  }

  public boolean isModified() {
    return myModified || myEditor != null && myEditor.isModified();
  }

  private void checkCanApply(FileTemplateTab list) throws ConfigurationException {
    final FileTemplate[] templates = myCurrentTab.getTemplates();
    final List<String> allNames = new ArrayList<String>();
    FileTemplate itemWithError = null;
    boolean errorInName = true;
    String errorString = null;
    for (FileTemplate template : templates) {
      if (isInternalTemplateName(template.getName())) {
        continue;
      }
      final String currName = template.getName();
      final String currExt = template.getExtension();
      if (currName.length() == 0) {
        itemWithError = template;
        errorString = IdeBundle.message("error.please.specify.template.name");
        break;
      }
      if (allNames.contains(currName)) {
        itemWithError = template;
        errorString = IdeBundle.message("error.template.with.such.name.already.exists");
        break;
      }
      if (currExt.length() == 0) {
        itemWithError = template;
        errorString = IdeBundle.message("error.please.specify.template.extension");
        errorInName = false;
        break;
      }
      allNames.add(currName);
    }
    
    if (itemWithError != null) {
      final boolean _errorInName = errorInName;
      myTabbedPane.setSelectedIndex(Arrays.asList(myTabs).indexOf(list));
      selectTemplate(itemWithError);
      list.selectTemplate(itemWithError);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (_errorInName) {
            myEditor.focusToNameField();
          }
          else {
            myEditor.focusToExtensionField();
          }
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

  public void apply() throws ConfigurationException {
    if (myEditor != null && myEditor.isModified()) {
      myModified = true;
      myEditor.apply();
    }
    
    for (FileTemplateTab list : myTabs) {
      checkCanApply(list);
    }

    final FileTemplateManager templatesManager = FileTemplateManager.getInstance();
    // Apply templates
    
    final List<FileTemplate> templates = new ArrayList<FileTemplate>();
    final List<FileTemplate> internalTemplates = new ArrayList<FileTemplate>();
    for (FileTemplate template : myTemplatesList.getTemplates()) {
      if (myInternalTemplateNames.contains(((FileTemplateBase)template).getQualifiedName())) {
        internalTemplates.add(template);
      }
      else {
        templates.add(template);
      }
    }
    
    templatesManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, templates);
    templatesManager.setTemplates(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY, internalTemplates);
    templatesManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, Arrays.asList(myIncludesList.getTemplates()));
    templatesManager.setTemplates(FileTemplateManager.CODE_TEMPLATES_CATEGORY, Arrays.asList(myCodeTemplatesList.getTemplates()));
    if (myJ2eeTemplatesList != null) {
      templatesManager.setTemplates(FileTemplateManager.J2EE_TEMPLATES_CATEGORY, Arrays.asList(myJ2eeTemplatesList.getTemplates()));
    }

    if (myEditor != null) {
      myModified = false;
      fireListChanged();
      reset();
    }
  }

  public void reset() {
    myEditor.reset();
    initLists();
    final PropertiesComponent component = PropertiesComponent.getInstance();
    final String tabName = component.getValue(CURRENT_TAB);
    int idx = 0;
    for (FileTemplateTab tab : myTabs) {
      if (Comparing.strEqual(tab.getTitle(), tabName)) {
        myCurrentTab = tab;
        myTabbedPane.setSelectedIndex(idx);
        final String selectedTemplateName = component.getValue(SELECTED_TEMPLATE);
        final FileTemplate[] templates = myCurrentTab.getTemplates();
        for (FileTemplate template : templates) {
          if (Comparing.strEqual(template.getName(), selectedTemplateName)) {
            tab.selectTemplate(template);
            break;
          }
        }
        break;
      }
      idx++;
    }
    myModified = false;
  }

  public void disposeUIResources() {
    if (myCurrentTab != null) {
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      propertiesComponent.setValue(CURRENT_TAB, myCurrentTab.getTitle());
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
    myJ2eeTemplatesList = null;
  }

  public void createNewTemplate(@NotNull String preferredName, @NotNull String extension, @NotNull String text) {
    createTemplate(preferredName, extension, text);
  }

  @NotNull
  public String getId() {
    return "fileTemplates";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
