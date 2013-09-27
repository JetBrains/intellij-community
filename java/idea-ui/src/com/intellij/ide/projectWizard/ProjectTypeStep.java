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

import com.intellij.framework.FrameworkGroup;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksPanel;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private JPanel myPanel;
  private JBList myProjectTypeList;
  private JPanel myOptionsPanel;

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
        return StringUtil.notNullize(getSelectedBuilder().getContentEntryPath());
      }
    };
    myConfigurationUpdater = new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        myFrameworksPanel.addSupport(module, rootModel);
      }
    };

    myProjectTypeList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        ProjectCategory category = (ProjectCategory)value;
        append(category.getDisplayName());
        setIcon(category.getIcon());
      }
    });

    List<ProjectCategory> categories = new ArrayList<ProjectCategory>();
    categories.addAll(ContainerUtil.map(ModuleBuilder.getAllBuilders(), new Function<ModuleBuilder, ProjectCategory>() {
      @Override
      public ProjectCategory fun(ModuleBuilder builder) {
        return new BuilderBasedProjectType(builder);
      }
    }));
    categories.addAll(Arrays.asList(ProjectCategory.EXTENSION_POINT_NAME.getExtensions()));

    final MultiMap<String, ProjectCategory> map = new MultiMap<String, ProjectCategory>();
    for (ProjectCategory category : categories) {
      map.putValue(category.getGroupName(), category);
    }
    Collections.sort(categories, new Comparator<ProjectCategory>() {
      @Override
      public int compare(ProjectCategory o1, ProjectCategory o2) {
        return map.get(o2.getGroupName()).size() - map.get(o1.getGroupName()).size();
      }
    });

    myProjectTypeList.setModel(new CollectionListModel<ProjectCategory>(categories));
    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ModuleBuilder builder = getSelectedBuilder();
        myContext.setProjectBuilder(builder);
        myWizard.getSequence().setType(builder.getBuilderId());
        builder.addModuleConfigurationUpdater(myConfigurationUpdater);
        updateOptionsPanel((ProjectCategory)myProjectTypeList.getSelectedValue());
      }
    });

    for (ProjectCategory category : categories) {
      myWizard.getSequence().addStepsForBuilder(myBuilders.get(category), context, modulesProvider, true);
    }

    myFrameworksPanel = new AddSupportForFrameworksPanel(Collections.<FrameworkSupportInModuleProvider>emptyList(), model, true);
    Disposer.register(wizard.getDisposable(), myFrameworksPanel);

    myOptionsPanel.add(myFrameworksPanel.getMainPanel(), FRAMEWORKS_CARD);
    myProjectTypeList.setSelectedIndex(0);
  }

  private ModuleBuilder getSelectedBuilder() {
    ProjectCategory projectCategory = (ProjectCategory)myProjectTypeList.getSelectedValue();
    return myBuilders.get(projectCategory);
  }

  private void updateOptionsPanel(ProjectCategory projectCategory) {
    if (projectCategory == null) return;
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
      List<FrameworkSupportInModuleProvider> providers = new ArrayList<FrameworkSupportInModuleProvider>();
      for (FrameworkSupportInModuleProvider framework : FrameworkSupportUtil.getAllProviders()) {
        if (matchFramework(projectCategory, framework)) {
          providers.add(framework);
        }
      }
      myFrameworksPanel.setProviders(providers);
      for (FrameworkSupportInModuleProvider provider : providers) {
        if (ArrayUtil.contains(provider.getFrameworkType().getId(), projectCategory.getAssociatedFrameworkIds())) {
          CheckedTreeNode treeNode = myFrameworksPanel.findNodeFor(provider);
          treeNode.setChecked(true);
        }
      }
    }
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, card);
  }

  private static boolean matchFramework(ProjectCategory projectCategory, FrameworkSupportInModuleProvider framework) {

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
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    myWizard.getSequence().addStepsForBuilder(getSelectedBuilder(), myContext, myModulesProvider, true);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectTypeList;
  }
}
