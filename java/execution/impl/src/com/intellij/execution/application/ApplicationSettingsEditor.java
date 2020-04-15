// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class ApplicationSettingsEditor extends FragmentedSettingsEditor<ApplicationConfiguration> {
  private final Project myProject;

  public ApplicationSettingsEditor(Project project) {
    myProject = project;
  }

  @Override
  protected Collection<SettingsEditorFragment<ApplicationConfiguration, ?>> createFragments() {
    List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments =
      new ArrayList<>(new CommonParameterFragments<ApplicationConfiguration>(myProject).getFragments());

    fragments.add(CommonTags.parallelRun());

    LabeledComponent<EditorTextFieldWithBrowseButton> mainClass = new LabeledComponent<>();
    mainClass.setLabelLocation(BorderLayout.WEST);
    mainClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true));

    JrePathEditor jrePathEditor = new JrePathEditor();
    jrePathEditor.getLabel().setVisible(false);
    jrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(myProject));

    LabeledComponent<RawCommandLineEditor> vmParams = LabeledComponent.create(new RawCommandLineEditor(),
                                                                              ExecutionBundle.message("run.configuration.java.vm.parameters.label"));
    vmParams.setLabelLocation(BorderLayout.WEST);
    fragments.addAll(Arrays.asList(
      new SettingsEditorFragment<>(null, jrePathEditor, 5,
                                   (configuration, editor) -> editor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled()),
                                   (configuration, editor) -> {
                                                configuration.setAlternativeJrePath(editor.getJrePathOrName());
                                                configuration.setAlternativeJrePathEnabled(editor.isAlternativeJreSelected());
                                             }),
      new SettingsEditorFragment<>(null, mainClass, 10,
                                   (configuration, component) -> component.getComponent().setText(configuration.getMainClassName()),
                                   (configuration, component) -> configuration.setMainClassName(component.getComponent().getText())),
      new SettingsEditorFragment<>(ExecutionBundle.message("run.configuration.java.vm.parameters.name"), vmParams,
                                   (configuration, component) -> component.getComponent().setText(configuration.getVMParameters()),
                                   (configuration, component) -> configuration.setVMParameters(component.getComponent().getText())),
      new SettingsEditorFragment<>(ExecutionBundle.message("redirect.input.from.name"), new ProgramInputRedirectPanel(),
                                   (configuration, panel) -> panel.reset(configuration.getInputRedirectOptions()),
                                   (configuration, panel) -> panel.applyTo(configuration.getInputRedirectOptions()))));
    return fragments;
  }

  @Override
  protected String getTitle() {
    return ExecutionBundle.message("application.configuration.title.build.and.run");
  }
}
