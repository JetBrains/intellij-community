// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jar;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.LabeledComponentNoThrow;
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
  private LabeledComponentNoThrow<ModulesComboBox> myModuleComponent;
  private JPanel myWholePanel;

  private JrePathEditor myJrePathEditor;
  private final Project myProject;
  private JComponent myAnchor;

  public JarApplicationConfigurable(final Project project) {
    myProject = project;
    myAnchor = UIUtil.mergeComponentsWithAnchor(myJarPathComponent, myCommonProgramParameters, myJrePathEditor);
    ModulesComboBox modulesComboBox = myModuleComponent.getComponent();
    modulesComboBox.allowEmptySelection(JavaCompilerBundle.message("whole.project"));
    modulesComboBox.fillModules(project);
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(modulesComboBox, true));
  }

  @Override
  public void applyEditorTo(@NotNull final JarApplicationConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setJarPath(FileUtil.toSystemIndependentName(myJarPathComponent.getComponent().getText()));
    configuration.setModule(myModuleComponent.getComponent().getSelectedModule());
  }

  @Override
  public void resetEditorFrom(@NotNull final JarApplicationConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myJarPathComponent.getComponent().setText(FileUtil.toSystemDependentName(configuration.getJarPath()));
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled()
    );
    myModuleComponent.getComponent().setSelectedModule(configuration.getModule());
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void createUIComponents() {
    myJarPathComponent = new LabeledComponent<>();
    var textFieldWithBrowseButton = new TextFieldWithBrowseButton();
    textFieldWithBrowseButton.addBrowseFolderListener(myProject, new FileChooserDescriptor(false, false, true, true, false, false)
      .withTitle(ExecutionBundle.message("choose.jar.file")));
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
