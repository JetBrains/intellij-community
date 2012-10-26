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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Condition;

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

  public SdkSettingsStep(SettingsStep settingsStep, Condition<SdkType> sdkFilter) {

    myWizardContext = settingsStep.getContext();
    myModel = new ProjectSdksModel();
    Project project = myWizardContext.getProject();
    myModel.reset(project, sdkFilter);

    myJdkComboBox = new JdkComboBox(myModel, sdkFilter);

    JButton button = new JButton("\u001BNew...");
    myJdkComboBox.setSetupButton(button, project, myModel,
                                 project == null ? new JdkComboBox.NoneJdkComboBoxItem() : new JdkComboBox.ProjectJdkComboBoxItem(),
                                 null,
                                 false);
    JPanel jdkPanel = new JPanel(new BorderLayout(4, 0));
    jdkPanel.add(myJdkComboBox);
    jdkPanel.add(button, BorderLayout.EAST);
    settingsStep.addSettingsField("Project \u001BSDK:", jdkPanel);

  }

  @Override
  public JComponent getComponent() {
    return null;
  }

  @Override
  public void updateDataModel() {
    Sdk jdk = myJdkComboBox.getSelectedJdk();
    myWizardContext.setProjectJdk(jdk);
  }

  @Override
  public boolean validate() throws ConfigurationException {
    myModel.apply();
    return true;
  }
}
