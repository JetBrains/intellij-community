// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Predicate;

public abstract class ModuleJdkConfigurable implements Disposable {
  private JdkComboBox myCbModuleJdk;
  private JPanel myJdkPanel;
  private ClasspathEditor myModuleEditor;
  private final ProjectSdksModel myJdksModel;
  private final ProjectStructureConfigurable myProjectStructureConfigurable;
  private boolean myFreeze = false;
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

  public ModuleJdkConfigurable(ClasspathEditor moduleEditor, ProjectStructureConfigurable projectStructureConfigurable) {
    myModuleEditor = moduleEditor;
    myJdksModel = projectStructureConfigurable.getProjectJdksModel();
    myProjectStructureConfigurable = projectStructureConfigurable;
    myJdksModel.addListener(myListener);
    init();
  }

  public JComponent createComponent() {
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    myCbModuleJdk.reloadModel();
    reset();
    myFreeze = false;
  }

  protected abstract ModifiableRootModel getRootModel();

  private void init() {
    final Project project = getRootModel().getModule().getProject();

    Predicate<SdkTypeId> predicate = SimpleJavaSdkType.notSimpleJavaSdkType();
    // TODO Use EelApi here.
    myCbModuleJdk = new JdkComboBox(project, myJdksModel, predicate::test,
                                    WslSdkFilter.filterSdkByWsl(project), WslSdkFilter.filterSdkSuggestionByWsl(project),
                                    null, jdk -> {
      final Sdk projectJdk = myJdksModel.getProjectSdk();
      if (projectJdk == null) {
        final int res =
          Messages.showYesNoDialog(myJdkPanel,
                                   JavaUiBundle.message("project.roots.no.jdk.on.project.message"),
                                   JavaUiBundle.message("project.roots.no.jdk.on.project.title"),
                                   Messages.getInformationIcon());
        if (res == Messages.YES) {
          myJdksModel.setProjectSdk(jdk);
        }
      }
    });
    myCbModuleJdk.showProjectSdkItem();
    myCbModuleJdk.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;

        final Sdk newJdk = myCbModuleJdk.getSelectedJdk();
        myModuleEditor.setSdk(newJdk);

        clearCaches();
      }
    });
    final JButton editButton = new JButton(ApplicationBundle.message("button.edit"));
    myCbModuleJdk.setEditButton(editButton, getRootModel().getModule().getProject(), () -> getRootModel().getSdk());
    myJdkPanel = new ModuleJdkConfigurableUi(myCbModuleJdk, editButton).getPanel();
  }

  private void clearCaches() {
    final Module module = getRootModel().getModule();
    final StructureConfigurableContext context = myProjectStructureConfigurable.getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
  }

  public void reset() {
    myFreeze = true;
    final String jdkName = getRootModel().getSdkName();
    if (jdkName != null && !getRootModel().isSdkInherited()) {
      Sdk selectedModuleJdk = myJdksModel.findSdk(jdkName);
      if (selectedModuleJdk != null) {
        myCbModuleJdk.setSelectedJdk(selectedModuleJdk);
      } else {
        myCbModuleJdk.setInvalidJdk(jdkName);
        clearCaches();
      }
    }
    else {
      myCbModuleJdk.setSelectedJdk(null);
    }
    myFreeze = false;
  }

  @Override
  public void dispose() {
    myModuleEditor = null;
    myCbModuleJdk = null;
    myJdkPanel = null;
    myJdksModel.removeListener(myListener);
  }
}
