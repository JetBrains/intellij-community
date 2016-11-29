/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.jar;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JarApplicationConfigurable extends SettingsEditor<JarApplicationConfiguration> implements PanelWithAnchor {
  private CommonJavaParametersPanel myCommonProgramParameters;
  private LabeledComponent<TextFieldWithBrowseButton> myJarPathComponent;
  private LabeledComponent<ModulesComboBox> myModuleComponent;
  private JPanel myWholePanel;

  private JrePathEditor myJrePathEditor;
  private final Project myProject;
  private JComponent myAnchor;

  public JarApplicationConfigurable(final Project project) {
    myProject = project;
    myAnchor = UIUtil.mergeComponentsWithAnchor(myJarPathComponent, myCommonProgramParameters, myJrePathEditor);
    ModulesComboBox modulesComboBox = myModuleComponent.getComponent();
    modulesComboBox.allowEmptySelection("<whole project>");
    modulesComboBox.fillModules(project);
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(modulesComboBox, true));
  }

  public void applyEditorTo(final JarApplicationConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setJarPath(FileUtil.toSystemIndependentName(myJarPathComponent.getComponent().getText()));
    configuration.setModule(myModuleComponent.getComponent().getSelectedModule());
  }

  public void resetEditorFrom(final JarApplicationConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myJarPathComponent.getComponent().setText(FileUtil.toSystemDependentName(configuration.getJarPath()));
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled()
    );
    myModuleComponent.getComponent().setSelectedModule(configuration.getModule());
  }

  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void createUIComponents() {
    myJarPathComponent = new LabeledComponent<>();
    TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
    textFieldWithBrowseButton.addBrowseFolderListener("Choose JAR File", null, myProject,
                                                      new FileChooserDescriptor(false, false, true, true, false, false));
    myJarPathComponent.setComponent(textFieldWithBrowseButton);
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
    myCommonProgramParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myJarPathComponent.setAnchor(anchor);
  }
}
