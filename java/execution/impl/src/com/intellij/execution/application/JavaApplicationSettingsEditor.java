// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RawCommandLineEditor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class JavaApplicationSettingsEditor extends FragmentedSettingsEditor<ApplicationConfiguration> {
  private final Project myProject;

  public JavaApplicationSettingsEditor(Project project) {
    myProject = project;
  }

  @Override
  protected Collection<SettingsEditorFragment<ApplicationConfiguration, ?>> createFragments() {
    List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments = new ArrayList<>();
    fragments.add(CommonTags.parallelRun());
    fragments.add(CommonParameterFragments.createRedirectFragment());

    fragments.addAll(new CommonParameterFragments<ApplicationConfiguration>(myProject).getFragments());

    JrePathEditor jrePathEditor = new JrePathEditor();
    jrePathEditor.getLabel().setVisible(false);
    jrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(myProject));

    LabeledComponent<RawCommandLineEditor> vmParams = LabeledComponent.create(new RawCommandLineEditor(),
                                                                              ExecutionBundle.message("run.configuration.java.vm.parameters.label"));
    vmParams.setLabelLocation(BorderLayout.WEST);
    String group = ExecutionBundle.message("group.java.options");
    fragments.add(new SettingsEditorFragment<>("jrePath", null, null, jrePathEditor, 5,
                                               (configuration, editor) -> editor
                                                 .setPathOrName(configuration.getAlternativeJrePath(),
                                                                configuration.isAlternativeJrePathEnabled()),
                                               (configuration, editor) -> {
                                                 configuration.setAlternativeJrePath(editor.getJrePathOrName());
                                                 configuration.setAlternativeJrePathEnabled(editor.isAlternativeJreSelected());
                                               },
                                               configuration -> true));
    fragments.add(new SettingsEditorFragment<>("mainClass", null, null, (EditorTextField)ClassEditorField.createClassField(myProject), 10,
                                               (configuration, component) -> component.setText(configuration.getMainClassName()),
                                               (configuration, component) -> configuration.setMainClassName(component.getText()),
                                               configuration -> true));
    fragments.add(new SettingsEditorFragment<>("vmParameters", ExecutionBundle.message("run.configuration.java.vm.parameters.name"), group, vmParams,
                                               (configuration, component) -> component.getComponent().setText(configuration.getVMParameters()),
                                               (configuration, component) -> configuration.setVMParameters(component.getComponent().getText()),
                                               configuration -> isNotEmpty(configuration.getVMParameters())));
    fragments.add(new TagFragment<>("formSnapshots", ExecutionBundle.message("show.swing.inspector.name"), group,
                                    configuration -> configuration.isSwingInspectorEnabled(),
                                    (configuration, enabled) -> configuration.setSwingInspectorEnabled(enabled)));
    return fragments;
  }

  @Override
  protected String getTitle() {
    return ExecutionBundle.message("application.configuration.title.build.and.run");
  }
}
