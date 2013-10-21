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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
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
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class ProjectTypeStep extends ModuleWizardStep {

  private static final String FRAMEWORKS_CARD = "frameworks card";
  private static final String GROUP_CARD = "group description card";
  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private JPanel myPanel;
  private JPanel myOptionsPanel;
  private JBLabel myGroupDescriptionLabel;
  private JBList myProjectTypeList;
  private final ProjectTypesList myList;

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
    MultiMap<String, ProjectCategory> map = new MultiMap<String, ProjectCategory>();
    for (TemplatesGroup group : groups) {
      String name = group.getName();
      for (ProjectTemplate template : templatesMap.get(group)) {
        TemplateBasedProjectType projectType = new TemplateBasedProjectType(template);
        map.putValue(name, projectType);
      }
      for (ProjectCategory category : categories.get(name)) {
        map.putValue(name, category);
      }
    }

    myList = new ProjectTypesList(myProjectTypeList, map, context);

    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

      @Override
      public void valueChanged(ListSelectionEvent e) {
        ModuleBuilder builder = getSelectedBuilder();
        if (builder != null) {
          myContext.setProjectBuilder(builder);
          myWizard.getSequence().setType(builder.getBuilderId());
          builder.addModuleConfigurationUpdater(myConfigurationUpdater);
        }
        updateOptionsPanel(getSelectedObject());
      }
    });

    for (ProjectCategory category : map.values()) {
      myWizard.getSequence().addStepsForBuilder(myBuilders.get(category), context, modulesProvider, false);
    }

    myFrameworksPanel = new AddSupportForFrameworksPanel(Collections.<FrameworkSupportInModuleProvider>emptyList(), model, true);
    Disposer.register(wizard.getDisposable(), myFrameworksPanel);

    myOptionsPanel.add(myFrameworksPanel.getMainPanel(), FRAMEWORKS_CARD);
  }

  @Nullable
  public ProjectCategory getSelectedObject() {
    return myList.getSelectedTemplate();
  }

  @Nullable
  private ModuleBuilder getSelectedBuilder() {
    ProjectCategory object = getSelectedObject();
    return object == null ? null : myBuilders.get(object);
  }

  private void updateOptionsPanel(Object object) {
    String card = GROUP_CARD;
    if (object instanceof ProjectCategory) {
      final ProjectCategory projectCategory = (ProjectCategory)object;
      ModuleBuilder builder = myBuilders.get(projectCategory);
      JComponent panel = builder.getCustomOptionsPanel(new Disposable() {
        @Override
        public void dispose() {
          disposeUIResources();
        }
      });
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

        myFrameworksPanel.setProviders(matched, new HashSet<String>(Arrays.asList(projectCategory.getAssociatedFrameworkIds())));
      }
    }
    else if (object instanceof TemplatesGroup) {
      myGroupDescriptionLabel.setText(((TemplatesGroup)object).getDescription());
    }
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, card);
  }

  private boolean matchFramework(ProjectCategory projectCategory, FrameworkSupportInModuleProvider framework) {

    if (!framework.isEnabledForModuleBuilder(myBuilders.get(projectCategory))) return false;

    FrameworkRole[] roles = framework.getRoles();
    if (roles.length == 0) return true;

    /*
    String[] ids = framework.getProjectCategories();
    if (ids.length > 0) {
      return ArrayUtil.contains(projectCategory.getId(), ids);
    }
    if (ArrayUtil.contains(framework.getFrameworkType().getId(), projectCategory.getAssociatedFrameworkIds())) return true;

    FrameworkGroup frameworkGroup = projectCategory.getAssociatedFrameworkGroup();
    FrameworkTypeEx frameworkType = framework.getFrameworkType();
    if (frameworkGroup != null) {
      return frameworkGroup == frameworkType.getParentGroup();
    }
    if (frameworkType.getParentGroup() != null) {
      return false;
    }

    String underlyingFrameworkTypeId = frameworkType.getUnderlyingFrameworkTypeId();
    if (underlyingFrameworkTypeId != null) {
      return ArrayUtil.contains(underlyingFrameworkTypeId, projectCategory.getAssociatedFrameworkIds());
    }
    else if (projectCategory.getAssociatedFrameworkIds().length > 0) {
      return false;
    }

    return framework.isEnabledForModuleBuilder(projectCategory.createModuleBuilder());
    */

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
    myWizard.getSequence().addStepsForBuilder(builder, myContext, myModulesProvider, true);
  }

  @Override
  public void updateStep() {
    myList.resetSelection();
  }

  @Override
  public void onStepLeaving() {
    myList.saveSelection();
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
    return myList.setSelectedTemplate(group, name);
  }
}
