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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.JBScrollPane;
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
  private static final int TEMPLATE_ID = 0;
  private static final int PATTERN_ID = 1;
  private static final int CODE_ID = 2;
  private static final int J2EE_ID = 3;
  private static final Icon ourIcon = IconLoader.getIcon("/general/fileTemplates.png");
  private FileTemplateTab[] myTabs;
  private static final String TEMPLATES_TITLE = IdeBundle.message("tab.filetemplates.templates");
  private static final String INCLUDES_TITLE = IdeBundle.message("tab.filetemplates.includes");
  private static final String CODE_TITLE = IdeBundle.message("tab.filetemplates.code");
  private static final String J2EE_TITLE = IdeBundle.message("tab.filetemplates.j2ee");
  private Disposable myUIDisposable;

  @NonNls private static final String CURRENT_TAB = "FileTemplates.CurrentTab";
  @NonNls private static final String SELECTED_TEMPLATE = "FileTemplates.SelectedTemplate";

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
      ext = defaultExtensions [0].value;
    }
    createTemplate(IdeBundle.message("template.unnamed"), ext, "");
  }

  private FileTemplate createTemplate(@NotNull String prefName, @NotNull @NonNls String extension, @NotNull String content) {
    FileTemplate[] templates = myCurrentTab.getTemplates();
    ArrayList<String> names = new ArrayList<String>(templates.length);
    for (FileTemplate template : templates) {
      names.add(template.getName());
    }
    String name = prefName;
    int i = 0;
    while (names.contains(name)) {
      name = prefName + " (" + ++i + ")";
    }
    FileTemplate newTemplate = new FileTemplateImpl(content, name, extension);
    myCurrentTab.addTemplate(newTemplate);
    myModified = true;
    myCurrentTab.selectTemplate(newTemplate);
    fireListChanged();
    myEditor.focusToNameField();
    return newTemplate;
  }

  private void onClone() {
    FileTemplate selected = myCurrentTab.getSelectedTemplate();
    if (selected == null) return;

    final FileTemplate[] templates = myCurrentTab.getTemplates();
    ArrayList<String> names = new ArrayList<String>(templates.length);
    for (FileTemplate template : templates) {
      names.add(template.getName());
    }
    @SuppressWarnings({"UnresolvedPropertyKey"})
    String nameTemplate = IdeBundle.message("template.copy.N.of.T");
    String name = MessageFormat.format(nameTemplate, "", selected.getName());
    int i = 0;
    while (names.contains(name)) {
      name = MessageFormat.format(nameTemplate, ++i + " ", selected.getName());
    }
    FileTemplate newTemplate = new FileTemplateImpl(selected.getText(), name, selected.getExtension());
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

    List<FileTemplateTab> allTabs = new ArrayList<FileTemplateTab>(Arrays.asList(myTemplatesList, myIncludesList, myCodeTemplatesList));

    final Set<FileTemplateGroupDescriptorFactory> factories = new THashSet<FileTemplateGroupDescriptorFactory>();
    factories.addAll(Arrays.asList(ApplicationManager.getApplication().getComponents(FileTemplateGroupDescriptorFactory.class)));
    factories.addAll(Arrays.asList(Extensions.getExtensions(FileTemplateGroupDescriptorFactory.EXTENSION_POINT_NAME)));

    if (!factories.isEmpty()) {
      myJ2eeTemplatesList = new FileTemplateTabAsTree(J2EE_TITLE) {
        public void onTemplateSelected() {
          onListSelectionChanged();
        }

        protected FileTemplateNode initModel() {
          SortedSet<FileTemplateGroupDescriptor> categories = new TreeSet<FileTemplateGroupDescriptor>(new Comparator<FileTemplateGroupDescriptor>() {
            public int compare(FileTemplateGroupDescriptor o1, FileTemplateGroupDescriptor o2) {
              return o1.getTitle().compareTo(o2.getTitle());
            }
          });


          for (FileTemplateGroupDescriptorFactory templateGroupFactory : factories) {
            ContainerUtil.addIfNotNull(templateGroupFactory.getFileTemplatesDescriptor(), categories);
          }

          //noinspection HardCodedStringLiteral
          return new FileTemplateNode("ROOT", null, ContainerUtil.map2List(categories, new Function<FileTemplateGroupDescriptor, FileTemplateNode>() {
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
      myTabbedPane.addTab(tab.getTitle(), new JBScrollPane(tab.getComponent()));
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
        FileTemplate selectedItem = myCurrentTab.getSelectedTemplate();
        FileTemplateManagerImpl manager = FileTemplateManagerImpl.getInstanceImpl();
        e.getPresentation().setEnabled(selectedItem != null
                                       && !selectedItem.isDefault()
                                       &&
                                       manager.getDefaultTemplate(selectedItem.getName(), selectedItem.getExtension()) !=
                                       null);
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
    if (selected != null) {
      if (Messages.showOkCancelDialog(IdeBundle.message("prompt.reset.to.original.template"),
                                      IdeBundle.message("title.reset.template"), Messages.getQuestionIcon()) !=
          DialogWrapper.OK_EXIT_CODE) {
        return;
      }
      FileTemplateImpl template = (FileTemplateImpl)selected;

      template.resetToDefault();
      myEditor.reset();
      myModified = true;
    }
  }

  private void onEditorChanged() {
    fireListChanged();
  }


  private void onTabChanged() {
    int selectedIndex = myTabbedPane.getSelectedIndex();
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
    VirtualFile defDesc = null;
    if (myCurrentTab == myTemplatesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl().getDefaultTemplateDescription();
    }
    else if (myCurrentTab == myIncludesList) {
      defDesc = FileTemplateManagerImpl.getInstanceImpl().getDefaultIncludeDescription();
    }
    if (myEditor.getTemplate() != template) {
      myEditor.setTemplate(template, defDesc);
      final boolean isInternal = isInternalTemplate(template.getName(), myCurrentTab.getTitle());
      myEditor.setShowInternalMessage(isInternal ? " " : null);
      myEditor.setShowAdjustCheckBox(myTemplatesList == myCurrentTab);
    }
  }

  // internal template could not be removed and should be rendered bold
  @SuppressWarnings({"SimplifiableIfStatement"})
  public static boolean isInternalTemplate(String templateName, String templateTabTitle) {
    if (templateName == null) return false;
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
      if (Comparing.strEqual(templateName, bean.name)) return true;
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
    FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate[] templates = templateManager.getAllTemplates();
    FileTemplate[] internals = templateManager.getInternalTemplates();
    FileTemplate[] templatesAndInternals = ArrayUtil.mergeArrays(internals, templates, FileTemplate.class);
    myTemplatesList.init(templatesAndInternals);
    myIncludesList.init(templateManager.getAllPatterns());
    myCodeTemplatesList.init(templateManager.getAllCodeTemplates());
    if (myJ2eeTemplatesList != null) {
      myJ2eeTemplatesList.init(templateManager.getAllJ2eeTemplates());
    }
  }

  public boolean isModified() {
    return myModified || myEditor != null && myEditor.isModified();
  }

  /**
   * If apply is acceptable, returns true. If no, returns false and fills error string.
   */
  private boolean canApply(final boolean showErrorDialog, String[] errorString) {
    for (FileTemplateTab list : myTabs) {
      if (!canApply(showErrorDialog, errorString, list)) return false;
    }
    return true;
  }

  private boolean canApply(final boolean showErrorDialog, String[] errorString, FileTemplateTab list) {
    final FileTemplate[] templates = myCurrentTab.getTemplates();
    ArrayList<String> allNames = new ArrayList<String>();
    FileTemplate itemWithError = null;
    String errorMessage = null;
    String errorTitle = null;
    boolean errorInName = true;
    for (FileTemplate template : templates) {
      if (isInternalTemplateName(template.getName())) continue;
      String currName = template.getName();
      String currExt = template.getExtension();
      if (currName.length() == 0) {
        itemWithError = template;
        errorMessage = IdeBundle.message("error.please.specify.a.name.for.this.template");
        errorTitle = IdeBundle.message("title.template.name.not.specified");
        errorString[0] = IdeBundle.message("error.please.specify.template.name");
        break;
      }
      if (allNames.contains(currName)) {
        itemWithError = template;
        errorMessage = IdeBundle.message("error.please.specify.a.different.name.for.this.template");
        errorTitle = IdeBundle.message("title.template.already.exists");
        errorString[0] = IdeBundle.message("error.template.with.such.name.already.exists");
        break;
      }
      if (currExt.length() == 0) {
        itemWithError = template;
        errorMessage = IdeBundle.message("error.please.specify.extension");
        errorTitle = IdeBundle.message("title.template.extension.not.specified");
        errorString[0] = IdeBundle.message("error.please.specify.template.extension");
        errorInName = false;
        break;
      }
      allNames.add(currName);
    }
    if (itemWithError == null) {
      return true;
    }
    else {
      final String _errorString = errorMessage;
      final String _errorTitle = errorTitle;
      final boolean _errorInName = errorInName;
      myTabbedPane.setSelectedIndex(Arrays.asList(myTabs).indexOf(list));
      selectTemplate(itemWithError);
      list.selectTemplate(itemWithError);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (showErrorDialog) {
              Messages.showMessageDialog(myMainPanel, _errorString, _errorTitle, Messages.getErrorIcon());
            }
            if (_errorInName) {
              myEditor.focusToNameField();
            }
            else {
              myEditor.focusToExtensionField();
            }
          }
        });
      return false;
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
    String[] errorString = new String[1];
    if (!canApply(false, errorString)) {
      throw new ConfigurationException(errorString[0]);
    }

    // Apply templates
    ArrayList<FileTemplate> newModifiedItems = new ArrayList<FileTemplate>();
    FileTemplate[] templates = myTemplatesList.getTemplates();
    newModifiedItems.addAll(Arrays.asList(templates));
    FileTemplateManager templatesManager = FileTemplateManager.getInstance();
    apply(newModifiedItems, myTemplatesList.savedTemplates, TEMPLATE_ID, templatesManager.getAllTemplates());

    // Apply patterns
    newModifiedItems = new ArrayList<FileTemplate>();
    templates = myIncludesList.getTemplates();
    newModifiedItems.addAll(Arrays.asList(templates));
    apply(newModifiedItems, myIncludesList.savedTemplates, PATTERN_ID, templatesManager.getAllPatterns());

    //Apply code templates
    newModifiedItems = new ArrayList<FileTemplate>();
    templates = myCodeTemplatesList.getTemplates();
    newModifiedItems.addAll(Arrays.asList(templates));
    apply(newModifiedItems, myCodeTemplatesList.savedTemplates, CODE_ID, templatesManager.getAllCodeTemplates());

    //Apply J2EE templates
    if (myJ2eeTemplatesList != null) {
      newModifiedItems = new ArrayList<FileTemplate>();
      templates = myJ2eeTemplatesList.getTemplates();
      newModifiedItems.addAll(Arrays.asList(templates));
      apply(newModifiedItems, myJ2eeTemplatesList.savedTemplates, J2EE_ID, templatesManager.getAllJ2eeTemplates());
    }

    FileTemplateManager.getInstance().saveAll();

    if (myEditor != null) {
      myModified = false;
      fireListChanged();
      reset();
    }
  }

  private static void removeTemplate(FileTemplate aTemplate, int listId, boolean fromDiskOnly) {
    FileTemplateManager manager = FileTemplateManager.getInstance();
    if (listId == TEMPLATE_ID) {
      if (!aTemplate.isInternal()) {
        manager.removeTemplate(aTemplate, fromDiskOnly);
      } else {
        manager.removeInternal(aTemplate);
      }
    }
    else if (listId == PATTERN_ID) {
      manager.removePattern(aTemplate, fromDiskOnly);
    }
    else if (listId == CODE_ID) {
      manager.removeCodeTemplate(aTemplate, fromDiskOnly);
    }
    else if (listId == J2EE_ID) {
      manager.removeJ2eeTemplate(aTemplate, fromDiskOnly);
    }
  }

  private static void apply(ArrayList<FileTemplate> newModifiedItems,
                            Map<FileTemplate,FileTemplate> savedTemplate2ModifiedTemplate,
                            int listId,
                            FileTemplate[] templates) {
    FileTemplateManager templatesManager = FileTemplateManager.getInstance();
    if (listId == TEMPLATE_ID) {
      FileTemplate[] internals = templatesManager.getInternalTemplates();
      templates = ArrayUtil.mergeArrays(internals, templates, FileTemplate.class);
    }
    ArrayList<FileTemplate> savedTemplates = new ArrayList<FileTemplate>();
    // Delete removed and fill savedTemplates
    for (FileTemplate aTemplate : templates) {
      FileTemplate aModifiedTemplate = savedTemplate2ModifiedTemplate.get(aTemplate);
      if (newModifiedItems.contains(aModifiedTemplate)) {
        savedTemplates.add(aTemplate);
      } else {
        removeTemplate(aTemplate, listId, false);
        savedTemplate2ModifiedTemplate.remove(aTemplate);
      }
    }
    // Now all removed templates deleted from table, savedTemplates contains all templates in table
    for (FileTemplate aTemplate : savedTemplates) {
      FileTemplate aModifiedTemplate = savedTemplate2ModifiedTemplate.get(aTemplate);
      LOG.assertTrue(aModifiedTemplate != null);
      aTemplate.setAdjust(aModifiedTemplate.isAdjust());
      if (!aModifiedTemplate.isDefault()) {
        FileTemplateUtil.copyTemplate(aModifiedTemplate, aTemplate);
      } else {
        if (!aTemplate.isDefault()) {
          removeTemplate(aTemplate, listId, true);
        }
      }
    }

    // Add new templates to table
    for (FileTemplate aModifiedTemplate : newModifiedItems) {
      LOG.assertTrue(aModifiedTemplate != null);
      if (!savedTemplate2ModifiedTemplate.containsValue(aModifiedTemplate)) {
        if (listId == TEMPLATE_ID) {
          templatesManager.addTemplate(aModifiedTemplate.getName(), aModifiedTemplate.getExtension()).setText(aModifiedTemplate.getText());
        }
        else if (listId == PATTERN_ID) {
          templatesManager.addPattern(aModifiedTemplate.getName(), aModifiedTemplate.getExtension()).setText(aModifiedTemplate.getText());
        }
        else if (listId == CODE_ID) {
          templatesManager.addCodeTemplate(aModifiedTemplate.getName(), aModifiedTemplate.getExtension()).setText(aModifiedTemplate.getText());
        }
        else if (listId == J2EE_ID) {
          templatesManager.addJ2eeTemplate(aModifiedTemplate.getName(), aModifiedTemplate.getExtension()).setText(aModifiedTemplate.getText());
        }
      }
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
        final String selectedTemplate = component.getValue(SELECTED_TEMPLATE);
        final FileTemplate[] templates = myCurrentTab.getTemplates();
        for (FileTemplate template : templates) {
          if (Comparing.strEqual(template.getName(), selectedTemplate)) {
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

  public String getId() {
    return "fileTemplates";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
