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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.TemplatesGroup;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplateEP;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.platform.templates.LocalArchivedTemplate;
import com.intellij.platform.templates.RemoteTemplatesFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
@SuppressWarnings("unchecked")
public class ProjectTypeStep extends ModuleWizardStep implements Disposable, ActionListener {

  private static final String DEFAULT_CARD = "default card";
  private static final String PROJECT_WIZARD_GROUP = "project.wizard.group";

  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private JPanel myPanel;
  private JPanel myOptionsPanel;
  private JBList myProjectTypeList;
  private ProjectTemplateList myTemplatesList;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<ProjectTemplate, ModuleBuilder> myBuilders = new FactoryMap<ProjectTemplate, ModuleBuilder>() {
    @Nullable
    @Override
    protected ModuleBuilder create(ProjectTemplate key) {
      return (ModuleBuilder)key.createModuleBuilder();
    }
  };
  private final Set<String> myCards = new HashSet<String>();
  private final MultiMap<TemplatesGroup,ProjectTemplate> myTemplatesMap;
  private boolean myRemoteTemplatesLoaded;

  public ProjectTypeStep(WizardContext context, NewProjectWizard wizard, ModulesProvider modulesProvider) {
    myContext = context;
    myWizard = wizard;
    myModulesProvider = modulesProvider;

    myTemplatesMap = new ConcurrentMultiMap<TemplatesGroup, ProjectTemplate>();
    List<TemplatesGroup> groups = fillTemplatesMap(context);

    myProjectTypeList.setModel(new CollectionListModel<TemplatesGroup>(groups));
    myProjectTypeList.setCellRenderer(new ColoredListCellRenderer<TemplatesGroup>() {
      @Override
      protected void customizeCellRenderer(JList list, TemplatesGroup value, int index, boolean selected, boolean hasFocus) {
        if (value.getParentGroup() != null) {
          append("         ");
        }
        else {
          setBorder(IdeBorderFactory.createEmptyBorder(2, 10, 2, 5));
          setIcon(value.getIcon());
        }
        append(value.getName());
      }
    });

    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        projectTypeChanged();
      }
    });

    myTemplatesList.setNewProject(context.isCreatingNewProject());
    myTemplatesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateSelection();
      }
    });

    for (ProjectTemplate category : myTemplatesMap.values()) {
      myWizard.getSequence().addStepsForBuilder(myBuilders.get(category), context, modulesProvider);
    }

    final String groupId = PropertiesComponent.getInstance().getValue(PROJECT_WIZARD_GROUP);
    if (groupId != null) {
      TemplatesGroup group = ContainerUtil.find(groups, new Condition<TemplatesGroup>() {
        @Override
        public boolean value(TemplatesGroup group) {
          return groupId.equals(group.getId());
        }
      });
      if (group != null) {
        myProjectTypeList.setSelectedValue(group, true);
      }
    }
    myTemplatesList.restoreSelection();
    if (myProjectTypeList.getSelectedValue() == null) {
      myProjectTypeList.setSelectedIndex(0);
    }
  }

  private List<TemplatesGroup> fillTemplatesMap(WizardContext context) {
    myTemplatesMap.putAllValues(CreateFromTemplateMode.getTemplatesMap(context));

    for (ProjectCategory category : ProjectCategory.EXTENSION_POINT_NAME.getExtensions()) {
      ArrayList<ProjectTemplate> templates = new ArrayList<ProjectTemplate>();
      templates.add(new ProjectCategoryTemplate(category));
      myTemplatesMap.put(new TemplatesGroup(category), templates);
    }
    if (context.isCreatingNewProject()) {
      MultiMap<String, ProjectTemplate> localTemplates = loadLocalTemplates();
      for (TemplatesGroup group : myTemplatesMap.keySet()) {
        myTemplatesMap.putValues(group, localTemplates.get(group.getId()));
      }
    }

    // remove empty groups
    for (Iterator<Map.Entry<TemplatesGroup, Collection<ProjectTemplate>>> iterator = myTemplatesMap.entrySet().iterator();
         iterator.hasNext(); ) {
      Map.Entry<TemplatesGroup, Collection<ProjectTemplate>> entry = iterator.next();
      if (entry.getValue().isEmpty()) {
        iterator.remove();
      }
    }

    List<TemplatesGroup> groups = new ArrayList<TemplatesGroup>(myTemplatesMap.keySet());

    // sorting by module type popularity
    final MultiMap<ModuleType, TemplatesGroup> moduleTypes = new MultiMap<ModuleType, TemplatesGroup>();
    for (TemplatesGroup group : groups) {
      ModuleType type = getModuleType(group);
      moduleTypes.putValue(type, group);
    }
    Collections.sort(groups, new Comparator<TemplatesGroup>() {
      @Override
      public int compare(TemplatesGroup o1, TemplatesGroup o2) {
        int u = Comparing.compare(ProjectTemplatesFactory.CUSTOM_GROUP.equals(o1.getName()), ProjectTemplatesFactory.CUSTOM_GROUP.equals(o2.getName()));
        if (u != 0) return u;
        int i1 = moduleTypes.get(getModuleType(o2)).size() - moduleTypes.get(getModuleType(o1)).size();
        if (i1 != 0) return i1;
        int i = myTemplatesMap.get(o2).size() - myTemplatesMap.get(o1).size();
        return i != 0 ? i : o1.compareTo(o2);
      }
    });

    Set<String> groupNames = ContainerUtil.map2Set(groups, new Function<TemplatesGroup, String>() {
      @Override
      public String fun(TemplatesGroup group) {
        return group.getName();
      }
    });

    // move subgroups
    MultiMap<String, TemplatesGroup> subGroups = new MultiMap<String, TemplatesGroup>();
    for (ListIterator<TemplatesGroup> iterator = groups.listIterator(); iterator.hasNext(); ) {
      TemplatesGroup group = iterator.next();
      String parentGroup = group.getParentGroup();
      if (parentGroup != null && groupNames.contains(parentGroup) && !group.getName().equals(parentGroup)) {
        subGroups.putValue(parentGroup, group);
        iterator.remove();
      }
    }
    for (ListIterator<TemplatesGroup> iterator = groups.listIterator(); iterator.hasNext(); ) {
      TemplatesGroup group = iterator.next();
      for (TemplatesGroup subGroup : subGroups.get(group.getName())) {
        iterator.add(subGroup);
      }
    }
    return groups;
  }

  private ModuleType getModuleType(TemplatesGroup group) {
    Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
    if (templates.isEmpty()) {
      return null;
    }
    ProjectTemplate template = templates.iterator().next();
    ModuleBuilder builder = myBuilders.get(template);
    return builder.getModuleType();
  }

  // new category or template is selected
  public void projectTypeChanged() {
    TemplatesGroup group = getSelectedGroup();
    if (group == null) return;
    PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, group.getId() );
    Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
    if (!selectCustomOptions(templates)) {
      setTemplatesList(group, templates, false);
      ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, DEFAULT_CARD);
    }
    updateSelection();
  }

  private void setTemplatesList(TemplatesGroup group, Collection<ProjectTemplate> templates, boolean preserveSelection) {
    ArrayList<ProjectTemplate> list = new ArrayList<ProjectTemplate>(templates);
    if (group.getParentGroup() == null) {
      for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
        if (group.getName().equals(templatesGroup.getParentGroup())) {
          list.addAll(myTemplatesMap.get(templatesGroup));
        }
      }
    }
    myTemplatesList.setTemplates(list, preserveSelection);
  }

  private boolean selectCustomOptions(Collection<ProjectTemplate> templates) {
    if (templates.size() == 1) {
      ModuleBuilder builder = myBuilders.get(templates.iterator().next());
      String card = builder.getBuilderId();
      if (!myCards.contains(card)) {
        JComponent panel = builder.getCustomOptionsPanel(this);
        if (panel == null) return false;
        myCards.add(card);
        myOptionsPanel.add(panel, card);
      }
      ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, card);
      return true;
    }
    return false;
  }

  private TemplatesGroup getSelectedGroup() {
    return (TemplatesGroup)myProjectTypeList.getSelectedValue();
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    TemplatesGroup group = getSelectedGroup();
    if (group == null) return null;
    Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
    if (templates.size() == 1) {
      ProjectTemplate template = templates.iterator().next();
      if (myCards.contains(myBuilders.get(template).getBuilderId())) {
        return template;
      }
    }
    return myTemplatesList.getSelectedTemplate();
  }

  @Nullable
  private ModuleBuilder getSelectedBuilder() {
    ProjectTemplate template = getSelectedTemplate();
    return template == null ? null : myBuilders.get(template);
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
    if (myContext.isCreatingNewProject() && !myRemoteTemplatesLoaded) {
      loadRemoteTemplates();
    }
  }

  @Override
  public void onStepLeaving() {
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectTypeList;
  }

  @TestOnly
  public boolean setSelectedTemplate(String group, String name) {
    ListModel model = myProjectTypeList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      if (group.equals(((TemplatesGroup)model.getElementAt(i)).getName())) {
        myProjectTypeList.setSelectedIndex(i);
        return myTemplatesList.setSelectedTemplate(name);
      }
    }
    return false;
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
              for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
                if (Comparing.equal(id, templatesGroup.getId()) || Comparing.equal(group, templatesGroup.getName())) {
                  myTemplatesMap.putValue(templatesGroup, template);
                }
              }
            }
          }
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              TemplatesGroup group = getSelectedGroup();
              if (group == null) return;
              Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
              setTemplatesList(group, templates, true);
            }
          });
        }
        finally {
          myTemplatesList.setPaintBusy(false);
          myRemoteTemplatesLoaded = true;
        }
      }
    });
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    updateSelection();
  }

  private void updateSelection() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      myContext.setProjectTemplate(template);
      ModuleBuilder builder = myBuilders.get(template);
      myContext.setProjectBuilder(builder);
      myWizard.getSequence().setType(builder.getBuilderId());
    }
  }
}
