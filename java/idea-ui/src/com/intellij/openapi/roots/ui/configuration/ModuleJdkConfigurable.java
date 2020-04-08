/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class ModuleJdkConfigurable implements Disposable {
  private JdkComboBox myCbModuleJdk;
  private JPanel myJdkPanel;
  private ClasspathEditor myModuleEditor;
  private final ProjectSdksModel myJdksModel;
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

  public ModuleJdkConfigurable(ClasspathEditor moduleEditor, ProjectSdksModel jdksModel) {
    myModuleEditor = moduleEditor;
    myJdksModel = jdksModel;
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

    myJdkPanel = new JPanel(new GridBagLayout());
    myCbModuleJdk = new JdkComboBox(project, myJdksModel, SimpleJavaSdkType.notSimpleJavaSdkType(), null, null, jdk -> {
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
    myJdkPanel.add(new JLabel(JavaUiBundle.message("module.libraries.target.jdk.module.radio")),
                   new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                          JBUI.insetsRight(6), 0, 0));
    myJdkPanel.add(myCbModuleJdk, new GridBagConstraints(1, 0, 1, 1, 0, 1.0,
                                                         GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                         JBUI.insetsRight(4), 0, 0));
    final JButton editButton = new JButton(ApplicationBundle.message("button.edit"));
    myCbModuleJdk.setEditButton(editButton, getRootModel().getModule().getProject(), () -> getRootModel().getSdk());
    myJdkPanel.add(editButton,
                   new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          JBUI.emptyInsets(), 0, 0));

    myJdkPanel.setBorder(JBUI.Borders.empty(6));
  }

  private void clearCaches() {
    final Module module = getRootModel().getModule();
    final Project project = module.getProject();
    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
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
