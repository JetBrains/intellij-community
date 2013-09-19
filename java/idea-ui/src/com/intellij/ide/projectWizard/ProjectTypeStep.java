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
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class ProjectTypeStep extends StepAdapter {

  private final WizardContext myContext;
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

  private final AddSupportForFrameworksPanel myFrameworksPanel;
  private final FrameworkSupportModelBase myModel;

  public ProjectTypeStep(WizardContext context, Disposable disposable) {
    myContext = context;
    Project project = context.getProject();
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(project);
    myModel = new FrameworkSupportModelBase(project, null, container) {
      @NotNull
      @Override
      public String getBaseDirectoryForLibrariesPath() {
        return StringUtil.notNullize(getSelectedBuilder().getContentEntryPath());
      }
    };

    ProjectCategory[] projectCategories = ProjectCategory.EXTENSION_POINT_NAME.getExtensions();
    myProjectTypeList.setModel(new CollectionListModel<ProjectCategory>(Arrays.asList(projectCategories)));
    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myContext.setProjectBuilder(getSelectedBuilder());
        updateFrameworks((ProjectCategory)myProjectTypeList.getSelectedValue());
      }
    });

    myFrameworksPanel = new AddSupportForFrameworksPanel(Collections.<FrameworkSupportInModuleProvider>emptyList(), myModel, true);
    Disposer.register(disposable, myFrameworksPanel);

    myOptionsPanel.add(myFrameworksPanel.getMainPanel(), BorderLayout.CENTER);
    myProjectTypeList.setSelectedIndex(0);
  }

  private ModuleBuilder getSelectedBuilder() {
    ProjectCategory projectCategory = (ProjectCategory)myProjectTypeList.getSelectedValue();
    return myBuilders.get(projectCategory);
  }

  private void updateFrameworks(ProjectCategory projectCategory) {
    if (projectCategory == null) return;
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
  public JComponent getPreferredFocusedComponent() {
    return myProjectTypeList;
  }
}
