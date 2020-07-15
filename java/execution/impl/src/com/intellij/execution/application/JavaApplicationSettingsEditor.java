// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.diagnostic.logging.LogsFragment;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.ui.*;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public final class JavaApplicationSettingsEditor extends RunConfigurationFragmentedEditor<ApplicationConfiguration> {
  private final Project myProject;

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration, JavaRunConfigurationExtensionManager.getInstance());
    myProject = configuration.getProject();
  }

  @Override
  protected List<SettingsEditorFragment<ApplicationConfiguration, ?>> createRunFragments() {
    List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments = new ArrayList<>();
    BeforeRunComponent beforeRunComponent = new BeforeRunComponent(this);
    fragments.add(BeforeRunFragment.createBeforeRun(beforeRunComponent, CompileStepBeforeRun.ID));
    fragments.addAll(BeforeRunFragment.createGroup());

    ModuleClasspathCombo.Item item = new ModuleClasspathCombo.Item(ExecutionBundle.message("application.configuration.include.provided.scope"));
    SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo>
      moduleClasspath = CommonJavaFragments.moduleClasspath(item, configuration -> configuration.isProvidedScopeIncluded(),
                                                            (configuration, value) -> configuration.setIncludeProvidedScope(value));
    ModuleClasspathCombo classpathCombo = moduleClasspath.component();
    Computable<Boolean> hasModule = () -> classpathCombo.getSelectedModule() != null;

    fragments.add(CommonTags.parallelRun());

    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments = new CommonParameterFragments<>(myProject, hasModule);
    fragments.add(commonParameterFragments.createRedirectFragment(hasModule));
    fragments.addAll(commonParameterFragments.getFragments());
    fragments.add(CommonJavaFragments.createBuildBeforeRun(beforeRunComponent));

    SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(myProject);
    fragments.add(jrePath);

    String group = ExecutionBundle.message("group.java.options");
    RawCommandLineEditor vmOptions = new RawCommandLineEditor();
    setMinimumWidth(vmOptions, 400);
    String message = ExecutionBundle.message("run.configuration.java.vm.parameters.empty.text");
    vmOptions.getEditorField().getAccessibleContext().setAccessibleName(message);
    vmOptions.getEditorField().getEmptyText().setText(message);
    MacrosDialog.addMacroSupport(vmOptions.getEditorField(), MacrosDialog.Filters.ALL, hasModule);
    FragmentedSettingsUtil.setupPlaceholderVisibility(vmOptions.getEditorField());
    SettingsEditorFragment<ApplicationConfiguration, RawCommandLineEditor> vmParameters =
      new SettingsEditorFragment<>("vmParameters", ExecutionBundle.message("run.configuration.java.vm.parameters.name"), group, vmOptions,
                                   15,
                                   (configuration, c) -> c.setText(configuration.getVMParameters()),
                                   (configuration, c) -> configuration.setVMParameters(c.isVisible() ? c.getText() : null),
                                   configuration -> isNotEmpty(configuration.getVMParameters()));
    vmParameters.setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"));
    vmParameters.setEditorGetter(editor -> editor.getEditorField());
    fragments.add(vmParameters);

    EditorTextField mainClass = ClassEditorField.createClassField(myProject, () -> classpathCombo.getSelectedModule());
    mainClass.setShowPlaceholderWhenFocused(true);
    UIUtil.setMonospaced(mainClass);
    String placeholder = ExecutionBundle.message("application.configuration.main.class.placeholder");
    mainClass.setPlaceholder(placeholder);
    mainClass.getAccessibleContext().setAccessibleName(placeholder);
    setMinimumWidth(mainClass, 300);
    SettingsEditorFragment<ApplicationConfiguration, EditorTextField> mainClassFragment =
      new SettingsEditorFragment<>("mainClass", ExecutionBundle.message("application.configuration.main.class"), null, mainClass, 20,
                                   (configuration, component) -> component.setText(configuration.getMainClassName()),
                                   (configuration, component) -> configuration.setMainClassName(component.getText()),
                                   configuration -> true);
    mainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.hint"));
    mainClassFragment.setRemovable(false);
    fragments.add(mainClassFragment);
    fragments.add(moduleClasspath);

    ShortenCommandLineModeCombo combo = new ShortenCommandLineModeCombo(myProject, jrePath.component(), () -> classpathCombo.getSelectedModule(),
                                                                        listener -> classpathCombo.addActionListener(listener));
    fragments.add(new SettingsEditorFragment<>("shorten.command.line",
                                               ExecutionBundle.message("application.configuration.shorten.command.line"),
                                               group, LabeledComponent.create(combo, ExecutionBundle.message("application.configuration.shorten.command.line.label"), BorderLayout.WEST),
                                               (configuration, c) -> c.getComponent().setItem(configuration.getShortenCommandLine()),
                                               (configuration, c) -> configuration.setShortenCommandLine(
                                                 c.isVisible() ? c.getComponent().getSelectedItem() : null),
                                               configuration -> configuration.getShortenCommandLine() != null));
    fragments.add(SettingsEditorFragment.createTag("formSnapshots", ExecutionBundle.message("show.swing.inspector.name"), group,
                                                   configuration -> configuration.isSwingInspectorEnabled(),
                                                   (configuration, enabled) -> configuration.setSwingInspectorEnabled(enabled)));

    fragments.add(new LogsFragment<>());
    return fragments;
  }
}
