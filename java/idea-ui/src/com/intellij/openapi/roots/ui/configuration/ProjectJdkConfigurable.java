// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

  public @Nullable Sdk getSelectedProjectJdk() {
    return myCbProjectJdk != null ? myJdksModel.findSdk(myCbProjectJdk.getSelectedJdk()) : null;
  }

  @Override
  public @NotNull JComponent createComponent() {
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
    if (modules.length == 0) return;

    StructureConfigurableContext context = rootConfigurable.getContext();
    ProjectStructureDaemonAnalyzer analyzer = context.getDaemonAnalyzer();
    analyzer.queueUpdates(ContainerUtil.map(modules, module -> new ModuleProjectStructureElement(context, module)));
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
