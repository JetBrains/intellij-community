/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.*;

public class ProjectJdkConfigurable implements UnnamedConfigurable {
  private static final Logger LOG = Logger.getInstance(ProjectJdkConfigurable.class);
  private JdkComboBox myCbProjectJdk;
  private JPanel myJdkPanel;
  private final Project myProject;
  private final ProjectStructureConfigurable myProjectStructureConfigurable;
  private final ProjectSdksModel myJdksModel;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    @Override
    public void sdkAdded(@NotNull Sdk sdk) {
      reloadModel();
    }

    @Override
    public void beforeSdkRemove(@NotNull Sdk sdk) {
      reloadModel();
    }

    @Override
    public void sdkChanged(@NotNull Sdk sdk, String previousName) {
      reloadModel();
    }

    @Override
    public void sdkHomeSelected(@NotNull Sdk sdk, @NotNull String newSdkHome) {
      reloadModel();
    }
  };

  private boolean myFreeze = false;

  public ProjectJdkConfigurable(ProjectStructureConfigurable projectStructureConfigurable, final ProjectSdksModel jdksModel) {
    myProject = projectStructureConfigurable.getProject();
    myProjectStructureConfigurable = projectStructureConfigurable;
    myJdksModel = jdksModel;
    myJdksModel.addListener(myListener);
  }

  @Nullable
  public Sdk getSelectedProjectJdk() {
    return myCbProjectJdk != null ? myJdksModel.findSdk(myCbProjectJdk.getSelectedJdk()) : null;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    if (myJdkPanel == null) {
      final var ui = new ProjectJdkConfigurableUi();
      myJdkPanel = ui.panel(myProject, myJdksModel);
      myCbProjectJdk = ui.getJdkComboBox();

      myCbProjectJdk.showNoneSdkItem();
      myCbProjectJdk.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myFreeze) return;
          if (myCbProjectJdk != null) {
            myJdksModel.setProjectSdk(myCbProjectJdk.getSelectedJdk());
          }
          clearCaches();
        }
      });

      final JButton editButton = ui.getEditButton();
      myCbProjectJdk.setEditButton(editButton, myProject, myJdksModel::getProjectSdk);
    }
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    final Sdk projectJdk = myJdksModel.getProjectSdk();
    if (myCbProjectJdk != null) {
      myCbProjectJdk.reloadModel();
      
      final String sdkName = projectJdk == null ? ProjectRootManager.getInstance(myProject).getProjectSdkName() : projectJdk.getName();
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
    }
    else {
      LOG.error("'createComponent' wasn't called before 'reset' for " + toString());
    }
    myFreeze = false;
  }

  private void clearCaches() {
    final ModuleStructureConfigurable rootConfigurable = myProjectStructureConfigurable.getModulesConfig();
    Module[] modules = rootConfigurable.getModules();
    for (Module module : modules) {
      final StructureConfigurableContext context = rootConfigurable.getContext();
      context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
    }
  }

  @Override
  public boolean isModified() {
    final Sdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    return !Comparing.equal(projectJdk, getSelectedProjectJdk());
  }

  @Override
  public void apply() {
    ProjectRootManager.getInstance(myProject).setProjectSdk(getSelectedProjectJdk());
  }

  @Override
  public void reset() {
    reloadModel();

    if (myCbProjectJdk != null) {
      final String sdkName = ProjectRootManager.getInstance(myProject).getProjectSdkName();
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
  }

  @Override
  public void disposeUIResources() {
    myJdksModel.removeListener(myListener);
    myJdkPanel = null;
    myCbProjectJdk = null;
  }

  void addChangeListener(ActionListener listener) {
    myCbProjectJdk.addActionListener(listener);
  }
}
