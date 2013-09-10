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
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelImpl;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
public class ProjectTypeStep extends StepAdapter {

  private JPanel myPanel;
  private JBList myProjectTypeList;
  private JPanel myFrameworksPanel;
  private JPanel myHeader;

  private final List<FrameworkPanel> myFrameworks = new ArrayList<FrameworkPanel>();
  private final FrameworkSupportModelBase myModel;

  public ProjectTypeStep(Project project) {

    final LibrariesContainer container = LibrariesContainerFactory.createContainer(project);
    myModel = new FrameworkSupportModelImpl(project, "", container);
    ProjectCategory[] projectCategories = ProjectCategory.EXTENSION_POINT_NAME.getExtensions();
    myProjectTypeList.setModel(new CollectionListModel<ProjectCategory>(Arrays.asList(projectCategories)));
    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateFrameworks((ProjectCategory)myProjectTypeList.getSelectedValue());
      }
    });
    myProjectTypeList.setSelectedIndex(0);

    myFrameworksPanel.setLayout(new VerticalFlowLayout());
    myHeader.setLayout(new VerticalFlowLayout());
  }

  private void updateFrameworks(ProjectCategory projectCategory) {
    myFrameworks.clear();
    myFrameworksPanel.removeAll();
    myHeader.removeAll();
    if (projectCategory != null) {
      List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getAllProviders();
      for (FrameworkSupportInModuleProvider framework : providers) {
        if (matchFramework(projectCategory, framework)) {
          addFramework(framework, projectCategory);
        }
      }
    }
    myHeader.setVisible(myHeader.getComponentCount() > 0);
    myPanel.revalidate();
    myPanel.repaint();
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

  private void addFramework(final FrameworkSupportInModuleProvider framework, ProjectCategory category) {

    FrameworkPanel frameworkPanel;
    if (ArrayUtil.contains(framework.getFrameworkType().getId(), category.getAssociatedFrameworkIds())) {
      frameworkPanel = new FrameworkPanel.HeaderPanel(framework, myModel);
      myHeader.add(frameworkPanel);
    }
    else {
      frameworkPanel = new FrameworkPanel(framework, myModel);
      myFrameworksPanel.add(frameworkPanel);
    }
    myFrameworks.add(frameworkPanel);
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
