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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ProjectJdkConfigurable implements UnnamedConfigurable {
  private JdkComboBox myCbProjectJdk;
  private JPanel myJdkPanel;
  private final Project myProject;
  private final ProjectSdksModel myJdksModel;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
      reloadModel();
    }

    public void beforeSdkRemove(Sdk sdk) {
      reloadModel();
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      reloadModel();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      reloadModel();
    }
  };

  private boolean myFreeze = false;

  public ProjectJdkConfigurable(Project project, final ProjectSdksModel jdksModel) {
    myProject = project;
    myJdksModel = jdksModel;
    myJdksModel.addListener(myListener);
    init();
  }

  @Nullable
  public Sdk getSelectedProjectJdk() {
    return myJdksModel.findSdk(myCbProjectJdk.getSelectedJdk());
  }

  public JComponent createComponent() {
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    final Sdk projectJdk = myJdksModel.getProjectSdk();
    myCbProjectJdk.reloadModel(new JdkComboBox.NoneJdkComboBoxItem(), myProject);
    final String sdkName = projectJdk == null ? ProjectRootManager.getInstance(myProject).getProjectJdkName() : projectJdk.getName();
    if (sdkName != null) {
      final Sdk jdk = myJdksModel.findSdk(sdkName);
      if (jdk != null) {
        myCbProjectJdk.setSelectedJdk(jdk);
      } else {
        myCbProjectJdk.setInvalidJdk(sdkName);
        clearCaches();
      }
    } else {
      myCbProjectJdk.setSelectedJdk(null);
    }
    myFreeze = false;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbProjectJdk = new JdkComboBox(myJdksModel);
    myCbProjectJdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);
    myCbProjectJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        final Sdk oldJdk = myJdksModel.getProjectSdk();
        myJdksModel.setProjectSdk(myCbProjectJdk.getSelectedJdk());
        clearCaches();
      }
    });
    myJdkPanel.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.project.radio")), new GridBagConstraints(0, 0, 3, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 4, 0), 0, 0));
    myJdkPanel.add(myCbProjectJdk, new GridBagConstraints(0, 1, 1, 1, 0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0));
    final JButton setUpButton = myCbProjectJdk.createSetupButton(myProject, myJdksModel, new JdkComboBox.NoneJdkComboBoxItem());
    myJdkPanel.add(setUpButton, new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0));
    myCbProjectJdk.appendEditButton(myProject, myJdkPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0), new Computable<Sdk>() {
      @Nullable
      public Sdk compute() {
        return myJdksModel.getProjectSdk();
      }
    });
  }

  private void clearCaches() {
    final ModuleStructureConfigurable rootConfigurable = ModuleStructureConfigurable.getInstance(myProject);
    Module[] modules = rootConfigurable.getModules();
    for (Module module : modules) {
      final StructureConfigurableContext context = rootConfigurable.getContext();
      context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
    }
  }

  public boolean isModified() {
    final Sdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    return !Comparing.equal(projectJdk, getSelectedProjectJdk());
  }

  public void apply() throws ConfigurationException {
    ProjectRootManager.getInstance(myProject).setProjectJdk(getSelectedProjectJdk());
  }

  public void reset() {
    reloadModel();

    final String sdkName = ProjectRootManager.getInstance(myProject).getProjectJdkName();
    if (sdkName != null) {
      final Sdk jdk = myJdksModel.findSdk(sdkName);
      if (jdk != null) {
        myCbProjectJdk.setSelectedJdk(jdk);
      } else {
        myCbProjectJdk.setInvalidJdk(sdkName);
      }
    } else {
      myCbProjectJdk.setSelectedJdk(null);
    }
  }

  public void disposeUIResources() {
    myJdksModel.removeListener(myListener);
    myJdkPanel = null;
    myCbProjectJdk = null;
  }

}
