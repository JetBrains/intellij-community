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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 *         Date: 10/23/12
 */
public class JavaSettingsStep extends ModuleWizardStep {

  private final ModuleBuilder myModuleBuilder;
  private JBCheckBox myCreateSourceRoot;
  private TextFieldWithBrowseButton mySourcePath;
  private JPanel myPanel;
  private final JdkComboBox myJdkComboBox;
  private final WizardContext myWizardContext;
  private final ProjectSdksModel myModel;

  public JavaSettingsStep(SettingsStep settingsStep, ModuleBuilder moduleBuilder, Condition<SdkType> sdkFilter) {
    myModuleBuilder = moduleBuilder;

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
    JPanel jdkPanel = new JPanel(new BorderLayout(SystemInfo.isMac? 0 : 2, 0));
    jdkPanel.add(myJdkComboBox);
    jdkPanel.add(button, BorderLayout.EAST);
    settingsStep.addSettingsField("Project \u001BSDK:", jdkPanel);

    if (moduleBuilder instanceof JavaModuleBuilder) {
      ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
        new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(
          IdeBundle.message("prompt.select.source.directory"), null, mySourcePath, project, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
          TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
          @Override
          protected void onFileChoosen(VirtualFile chosenFile) {
            String contentEntryPath = myModuleBuilder.getContentEntryPath();
            String path = chosenFile.getPath();
            if (contentEntryPath != null) {

              int i = StringUtil.commonPrefixLength(contentEntryPath, path);
              mySourcePath.setText(path.substring(i));
            }
            else {
              mySourcePath.setText(path);
            }
          }
        };
      mySourcePath.addBrowseFolderListener(project, listener);
      myCreateSourceRoot.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          mySourcePath.setEnabled(myCreateSourceRoot.isSelected());
        }
      });
      settingsStep.addExpertPanel(myPanel);
    }

  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    Sdk jdk = myJdkComboBox.getSelectedJdk();
    myWizardContext.setProjectJdk(jdk);

    if (myModuleBuilder instanceof JavaModuleBuilder && myCreateSourceRoot.isSelected()) {
      String contentEntryPath = myModuleBuilder.getContentEntryPath();
      if (contentEntryPath != null) {
        final String dirName = mySourcePath.getText().trim().replace(File.separatorChar, '/');
        String text = dirName.length() > 0? contentEntryPath + "/" + dirName : contentEntryPath;
        ((JavaModuleBuilder)myModuleBuilder).setSourcePaths(Collections.singletonList(Pair.create(text, "")));
      }
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    myModel.apply();
    return true;
  }
}
