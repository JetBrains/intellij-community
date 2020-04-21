// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class CommonParameterFragments<Settings extends CommonProgramRunConfigurationParameters> {

  private final List<SettingsEditorFragment<Settings, ?>> myFragments = new ArrayList<>();

  public CommonParameterFragments(@NotNull Project project) {
    myFragments.add(new SettingsEditorFragment<>("commandLineParameters", null, new RawCommandLineEditor(),
                                                 100,
                                                 (settings, component) -> component.setText(settings.getProgramParameters()),
                                                 (settings, component) -> settings.setProgramParameters(component.getText()),
                                                 settings -> true));

    TextFieldWithBrowseButton workingDirectoryField = new TextFieldWithBrowseButton();
    workingDirectoryField.addBrowseFolderListener(ExecutionBundle.message("select.working.directory.message"), null,
                                                    project,
                                                    FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                    TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    LabeledComponent<TextFieldWithBrowseButton> field = LabeledComponent.create(workingDirectoryField,
                                                                                     ExecutionBundle.message(
                                                                                       "run.configuration.working.directory.label"));
    field.setLabelLocation(BorderLayout.WEST);
    myFragments.add(new SettingsEditorFragment<>("workingDirectory", null, field,
                                                 (settings, component) -> component.getComponent().setText(settings.getWorkingDirectory()),
                                                 (settings, component) -> settings.setWorkingDirectory(component.getComponent().getText()),
                                                 settings -> isNotEmpty(settings.getWorkingDirectory())));
    EnvironmentVariablesComponent env = new EnvironmentVariablesComponent();
    env.setLabelLocation(BorderLayout.WEST);
    myFragments.add(SettingsEditorFragment.create("environmentVariables",
                                                  ExecutionBundle.message("environment.variables.fragment.name"), env));
  }

  public List<SettingsEditorFragment<Settings, ?>> getFragments() {
    return myFragments;
  }
}
