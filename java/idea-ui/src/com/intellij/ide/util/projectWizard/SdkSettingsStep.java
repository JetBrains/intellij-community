/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 *         Date: 10/26/12
 */
public class SdkSettingsStep extends ModuleWizardStep {
  protected final JdkComboBox myJdkComboBox;
  protected final WizardContext myWizardContext;
  protected final ProjectSdksModel myModel;
  private final ModuleBuilder myModuleBuilder;

  public SdkSettingsStep(SettingsStep settingsStep, ModuleBuilder moduleBuilder, @NotNull Condition<SdkTypeId> sdkFilter) {
    myModuleBuilder = moduleBuilder;

    myWizardContext = settingsStep.getContext();
    myModel = new ProjectSdksModel();
    Project project = myWizardContext.getProject();
    myModel.reset(project);

    myJdkComboBox = new JdkComboBox(myModel, sdkFilter);

    if (project != null) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null && moduleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        // use project SDK
        return;
      }
    }
    else  {
      // set default project SDK
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      Sdk sdk = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
      if (sdk != null && sdkFilter.value(sdk.getSdkType())) {
        myJdkComboBox.setSelectedJdk(sdk);
      }
    }

    JButton button = new JButton("Ne\u001Bw...");
    myJdkComboBox.setSetupButton(button, project, myModel,
                                 project == null ? new JdkComboBox.NoneJdkComboBoxItem() : new JdkComboBox.ProjectJdkComboBoxItem(),
                                 null,
                                 false);
    JPanel jdkPanel = new JPanel(new BorderLayout(4, 0));
    jdkPanel.add(myJdkComboBox);
    jdkPanel.add(button, BorderLayout.EAST);
    settingsStep.addSettingsField((project == null ? "Project" : "Module") + " \u001BSDK:", jdkPanel);

  }

  @Override
  public JComponent getComponent() {
    return null;
  }

  @Override
  public void updateDataModel() {
    Project project = myWizardContext.getProject();
    if (project == null) {
      Sdk jdk = myJdkComboBox.getSelectedJdk();
      myWizardContext.setProjectJdk(jdk);
    }
    else {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk == null || !myModuleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        myModuleBuilder.setModuleJdk(myJdkComboBox.getSelectedJdk());
      } // else, inherit project jdk
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (myJdkComboBox.getSelectedJdk() == null && !ApplicationManager.getApplication().isUnitTestMode()) {
      int result = Messages.showDialog(getNoSdkMessage(),
                                       IdeBundle.message("title.no.jdk.specified"),
                                       new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1, Messages.getWarningIcon());
      if (result != Messages.YES) {
        return false;
      }
    }
    try {
      myModel.apply();
    } catch (ConfigurationException e) {
      //IDEA-98382 We should allow Next step if user has wrong SDK
    }
    return true;
  }

  protected String getNoSdkMessage() {
    return IdeBundle.message("prompt.confirm.project.no.jdk");
  }
}
