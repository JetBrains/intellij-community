/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksPanel;
import com.intellij.ide.util.newProjectWizard.TemplatesGroup;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplateEP;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.platform.templates.LocalArchivedTemplate;
import com.intellij.platform.templates.RemoteTemplatesFactory;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class ProjectTypeStep extends ModuleWizardStep implements Disposable {

  private static final String FRAMEWORKS_CARD = "frameworks card";
  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private final JTextPane myTemplateDescription;
  private JPanel myPanel;
  private JPanel myOptionsPanel;
  private JBList myProjectTypeList;

  private final ProjectTypesList myProjectTypesList;
  private final JBList myTemplatesList;
  private final TabInfo myFrameworksTab;
  private final TabInfo myTemplatesTab;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<ProjectCategory, ModuleBuilder> myBuilders = new FactoryMap<ProjectCategory, ModuleBuilder>() {
    @Nullable
    @Override
    protected ModuleBuilder create(ProjectCategory key) {
      return key.createModuleBuilder();
    }
  };
  private final Set<String> myCards = new HashSet<String>();

  private final AddSupportForFrameworksPanel myFrameworksPanel;
  private final ModuleBuilder.ModuleConfigurationUpdater myConfigurationUpdater;
  private boolean myCommitted;
  private final JBTabsImpl myTabs;
  private final MultiMap<String, ProjectTemplate> myTemplates = loadLocalTemplates();

  public ProjectTypeStep(WizardContext context, NewProjectWizard wizard, ModulesProvider modulesProvider) {
    myContext = context;
    myWizard = wizard;
    myModulesProvider = modulesProvider;
    Project project = context.getProject();
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(project);
    FrameworkSupportModelBase model = new FrameworkSupportModelBase(project, null, container) {
      @NotNull
      @Override
      public String getBaseDirectoryForLibrariesPath() {
        ModuleBuilder builder = getSelectedBuilder();
        assert builder != null;
        return StringUtil.notNullize(builder.getContentEntryPath());
      }
    };
    myConfigurationUpdater = new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        myFrameworksPanel.addSupport(module, rootModel);
      }
    };

    final MultiMap<String, ProjectCategory> categories = new MultiMap<String, ProjectCategory>();
    for (ProjectCategory category : ProjectCategory.EXTENSION_POINT_NAME.getExtensions()) {
      categories.putValue(category.getGroupName(), category);
    }

    MultiMap<TemplatesGroup,ProjectTemplate> templatesMap = CreateFromTemplateMode.getTemplatesMap(context, false);
    List<TemplatesGroup> groups = new ArrayList<TemplatesGroup>(templatesMap.keySet());
    Collections.sort(groups);
    MultiMap<TemplatesGroup, ProjectCategory> map = new MultiMap<TemplatesGroup, ProjectCategory>();
    for (TemplatesGroup group : groups) {
      for (ProjectTemplate template : templatesMap.get(group)) {
        TemplateBasedProjectType projectType = new TemplateBasedProjectType(template);
        map.putValue(group, projectType);
      }
      for (ProjectCategory category : categories.get(group.getName())) {
        map.putValue(group, category);
      }
    }

    myProjectTypesList = new ProjectTypesList(myProjectTypeList, map, myBuilders);

    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        projectTypeChanged(true);
      }
    });

    for (ProjectCategory category : map.values()) {
      myWizard.getSequence().addStepsForBuilder(myBuilders.get(category), context, modulesProvider);
    }
    myFrameworksPanel = new AddSupportForFrameworksPanel(Collections.<FrameworkSupportInModuleProvider>emptyList(), model, true);
    Disposer.register(wizard.getDisposable(), myFrameworksPanel);

    myTemplatesList = new JBList();
    myTemplatesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTemplatesList.setCellRenderer(new ColoredListCellRenderer<ProjectCategory>() {
      @Override
      protected void customizeCellRenderer(JList list, ProjectCategory value, int index, boolean selected, boolean hasFocus) {
        append(value.getDisplayName()).setIcon(value.getIcon());
      }
    });
    myTemplatesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        projectTypeChanged(false);
        ProjectCategory type = (ProjectCategory)myTemplatesList.getSelectedValue();
        myTemplateDescription.setText(type == null ? "" : type.getDescription());
      }
    });

    myTabs = new JBTabsImpl(null, IdeFocusManager.findInstance(), this);
    myFrameworksTab = new TabInfo(myFrameworksPanel.getMainPanel()).setText("  Frameworks  ");
    myTabs.addTab(myFrameworksTab);

    JPanel templatesPanel = new JPanel(new BorderLayout());
    templatesPanel.add(ScrollPaneFactory.createScrollPane(myTemplatesList, SideBorder.BOTTOM));
    myTemplateDescription = new JTextPane();
    myTemplateDescription.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    Messages.installHyperlinkSupport(myTemplateDescription);
    templatesPanel.add(myTemplateDescription, BorderLayout.SOUTH);

    myTemplatesTab = new TabInfo(templatesPanel).setText("   Templates   ");
    myTabs.addTab(myTemplatesTab);
    myOptionsPanel.add(myTabs.getComponent(), FRAMEWORKS_CARD);

    myTabs.addListener(new TabsListener.Adapter() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        projectTypeChanged(false);
      }
    });
  }

  // new category or template is selected
  public void projectTypeChanged(boolean updatePanel) {
    ModuleBuilder builder = getSelectedBuilder();
    if (builder != null) {
      myContext.setProjectBuilder(builder);
      myWizard.getSequence().setType(builder.getBuilderId());
      if (myFrameworksTab == myTabs.getSelectedInfo()) {
        builder.addModuleConfigurationUpdater(myConfigurationUpdater);
      }
    }
    if (updatePanel) {
      ProjectCategory type = getSelectedProjectType();
      if (type != null) {
        updateOptionsPanel(type);
      }
    }
  }

  @Nullable
  public ProjectCategory getSelectedProjectType() {
    if (myTabs.getSelectedInfo() == myFrameworksTab) {
      return myProjectTypesList.getSelectedType();
    }
    else {
      return (ProjectCategory)myTemplatesList.getSelectedValue();
    }
  }

  @Nullable
  private ModuleBuilder getSelectedBuilder() {
    ProjectCategory object = getSelectedProjectType();
    return object == null ? null : myBuilders.get(object);
  }

  private void updateOptionsPanel(final @NotNull ProjectCategory projectCategory) {
    ModuleBuilder builder = myBuilders.get(projectCategory);
    JComponent panel = builder.getCustomOptionsPanel(new Disposable() {
      @Override
      public void dispose() {
        disposeUIResources();
      }
    });
    String card;
    if (panel != null) {
      card = builder.getBuilderId();
      if (myCards.add(card)) {
        myOptionsPanel.add(panel, card);
      }
    }
    else {
      card = FRAMEWORKS_CARD;
      List<FrameworkSupportInModuleProvider> allProviders = FrameworkSupportUtil.getProviders(builder);
      List<FrameworkSupportInModuleProvider> matched =
        ContainerUtil.filter(allProviders, new Condition<FrameworkSupportInModuleProvider>() {
          @Override
          public boolean value(FrameworkSupportInModuleProvider provider) {
            return matchFramework(projectCategory, provider);
          }
        });

      myFrameworksPanel.setProviders(matched,
                                     new HashSet<String>(Arrays.asList(projectCategory.getAssociatedFrameworkIds())),
                                     new HashSet<String>(Arrays.asList(projectCategory.getPreselectedFrameworkIds())));

      boolean hasTemplates = updateTemplates(projectCategory, true);
      myFrameworksTab.setEnabled(!matched.isEmpty() || !hasTemplates);
    }
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, card);
  }

  private boolean updateTemplates(ProjectCategory projectCategory, boolean initial) {
    List<ProjectCategory> templates = ContainerUtil.map(myTemplates.get(projectCategory.getId()), new Function<ProjectTemplate, ProjectCategory>() {
      @Override
      public ProjectCategory fun(ProjectTemplate template) {
        return new TemplateBasedProjectType(template);
      }
    });

    //noinspection unchecked
    myTemplatesList.setModel(new CollectionListModel<ProjectCategory>(templates));
    myTemplatesTab.setEnabled(!templates.isEmpty());
    if (initial && !templates.isEmpty()) {
      myTemplatesList.setSelectedIndex(0);
    }
    return !templates.isEmpty();
  }

  private boolean matchFramework(ProjectCategory projectCategory, FrameworkSupportInModuleProvider framework) {

    if (!framework.isEnabledForModuleBuilder(myBuilders.get(projectCategory))) return false;

    FrameworkRole[] roles = framework.getRoles();
    if (roles.length == 0) return true;

    List<FrameworkRole> acceptable = Arrays.asList(projectCategory.getAcceptableFrameworkRoles());
    return ContainerUtil.intersects(Arrays.asList(roles), acceptable);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    ModuleBuilder builder = getSelectedBuilder();
    assert builder != null;
    myWizard.getSequence().addStepsForBuilder(builder, myContext, myModulesProvider);
  }

  @Override
  public void updateStep() {
    myProjectTypesList.resetSelection();
    loadRemoteTemplates();
  }

  @Override
  public void onStepLeaving() {
    myProjectTypesList.saveSelection();
  }

  @Override
  public void onWizardFinished() throws CommitStepException {
    if (!myCommitted && myTabs.getSelectedInfo() == myFrameworksTab) {
      boolean ok = myFrameworksPanel.downloadLibraries();
      if (!ok) {
        int answer = Messages.showYesNoDialog(getComponent(),
                                              ProjectBundle.message("warning.message.some.required.libraries.wasn.t.downloaded"),
                                              CommonBundle.getWarningTitle(), Messages.getWarningIcon());
        if (answer != Messages.YES) {
          throw new CommitStepException(null);
        }
      }
      myCommitted = true;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectTypeList;
  }

  @TestOnly
  public AddSupportForFrameworksPanel getFrameworksPanel() {
    return myFrameworksPanel;
  }

  @TestOnly
  public boolean setSelectedProjectType(String group, String name) {
    return myProjectTypesList.setSelectedType(group, name);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  private static MultiMap<String, ProjectTemplate> loadLocalTemplates() {
    ConcurrentMultiMap<String, ProjectTemplate> map = new ConcurrentMultiMap<String, ProjectTemplate>();
    ProjectTemplateEP[] extensions = ProjectTemplateEP.EP_NAME.getExtensions();
    for (ProjectTemplateEP ep : extensions) {
      ClassLoader classLoader = ep.getLoaderForClass();
      URL url = classLoader.getResource(ep.templatePath);
      if (url != null) {
        map.putValue(ep.projectType, new LocalArchivedTemplate(url, classLoader));
      }
    }
    return map;
  }

  private void loadRemoteTemplates() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myContext.getProject(), "Loading Templates") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myTemplatesList.setPaintBusy(true);
          RemoteTemplatesFactory factory = new RemoteTemplatesFactory();
          String[] groups = factory.getGroups();
          for (String group : groups) {
            ProjectTemplate[] templates = factory.createTemplates(group, myContext);
            for (ProjectTemplate template : templates) {
              String id = ((ArchivedProjectTemplate)template).getCategory();
              myTemplates.putValue(id == null ? group : id, template);
            }
         }
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              int index = myTemplatesList.getSelectedIndex();
              ProjectCategory type = myProjectTypesList.getSelectedType();
              if (type != null) {
                updateTemplates(type, false);
              }
              myTemplatesList.setSelectedIndex(index);
            }
          });
        }
        finally {
          myTemplatesList.setPaintBusy(false);
        }
      }
    });

  }
}
