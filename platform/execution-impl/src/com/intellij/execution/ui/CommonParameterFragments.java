// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.*;

public class CommonParameterFragments<Settings extends CommonProgramRunConfigurationParameters> {

  private final List<SettingsEditorFragment<Settings, ?>> myFragments = new ArrayList<>();

  public CommonParameterFragments(@NotNull Project project) {
    RawCommandLineEditor commandLineEditor = new RawCommandLineEditor();
    commandLineEditor.getEditorField().getEmptyText().setText(ExecutionBundle.message("run.configuration.program.hint"));
    myFragments.add(new SettingsEditorFragment<>("commandLineParameters", null, null, commandLineEditor,
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
    myFragments.add(new SettingsEditorFragment<>("workingDirectory", null, null, field,
                                                 (settings, component) -> component.getComponent().setText(settings.getWorkingDirectory()),
                                                 (settings, component) -> settings.setWorkingDirectory(component.getComponent().getText()),
                                                 settings -> isNotEmpty(settings.getWorkingDirectory())));

    EnvironmentVariablesComponent env = new EnvironmentVariablesComponent();
    env.setLabelLocation(BorderLayout.WEST);
    myFragments.add(SettingsEditorFragment.create("environmentVariables",
                                                  ExecutionBundle.message("environment.variables.fragment.name"), ExecutionBundle.message("group.java.options"), env));
  }

  public List<SettingsEditorFragment<Settings, ?>> getFragments() {
    return myFragments;
  }

  public static <S extends InputRedirectAware> SettingsEditorFragment<S, ?> createRedirectFragment() {

    TextFieldWithBrowseButton inputFile = new TextFieldWithBrowseButton();
    inputFile.addBrowseFolderListener(null, null, null,
                                      FileChooserDescriptorFactory.createSingleFileDescriptor(),
                                      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    LabeledComponent<TextFieldWithBrowseButton> labeledComponent =
      LabeledComponent.create(inputFile, ExecutionBundle.message("redirect.input.from"));
    labeledComponent.setLabelLocation(BorderLayout.WEST);
    return new SettingsEditorFragment<>("redirectInput", ExecutionBundle.message("redirect.input.from.name"),
                                        ExecutionBundle.message("group.operating.system"), labeledComponent,
                                        (settings, component) -> component.getComponent().setText(
                                          FileUtil.toSystemDependentName(notNullize(settings.getInputRedirectOptions().getRedirectInputPath()))),
                                        (settings, component) -> {
                                          String filePath = component.getComponent().getText();
                                          settings.getInputRedirectOptions().setRedirectInput(isNotEmpty(filePath));
                                          settings.getInputRedirectOptions().setRedirectInputPath(
                                            isEmpty(filePath) ? null : FileUtil.toSystemIndependentName(filePath));
                                        },
                                        settings -> isNotEmpty(settings.getInputRedirectOptions().getRedirectInputPath()));
  }
}
