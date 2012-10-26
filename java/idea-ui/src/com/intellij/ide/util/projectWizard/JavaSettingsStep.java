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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 *         Date: 10/23/12
 */
public class JavaSettingsStep extends SdkSettingsStep {

  private final ModuleBuilder myModuleBuilder;
  private JBCheckBox myCreateSourceRoot;
  private TextFieldWithBrowseButton mySourcePath;
  private JPanel myPanel;

  public JavaSettingsStep(SettingsStep settingsStep, ModuleBuilder moduleBuilder, Condition<SdkType> sdkFilter) {
    super(settingsStep, sdkFilter);
    myModuleBuilder = moduleBuilder;

    if (moduleBuilder instanceof JavaModuleBuilder) {
      Project project = settingsStep.getContext().getProject();
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
  public void updateDataModel() {
    super.updateDataModel();
    if (myModuleBuilder instanceof JavaModuleBuilder && myCreateSourceRoot.isSelected()) {
      String contentEntryPath = myModuleBuilder.getContentEntryPath();
      if (contentEntryPath != null) {
        final String dirName = mySourcePath.getText().trim().replace(File.separatorChar, '/');
        String text = dirName.length() > 0? contentEntryPath + "/" + dirName : contentEntryPath;
        ((JavaModuleBuilder)myModuleBuilder).setSourcePaths(Collections.singletonList(Pair.create(text, "")));
      }
    }
  }
}
