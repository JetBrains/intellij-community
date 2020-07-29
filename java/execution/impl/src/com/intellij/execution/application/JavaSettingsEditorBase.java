// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.diagnostic.logging.LogsFragment;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.execution.ui.*;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public abstract class JavaSettingsEditorBase<T extends JavaRunConfigurationBase> extends RunConfigurationFragmentedEditor<T> {
  protected final Project myProject;

  public JavaSettingsEditorBase(T runConfiguration, RunConfigurationExtensionsManager extensionsManager) {
    super(runConfiguration, extensionsManager);
    myProject = runConfiguration.getProject();
  }

  @Override
  protected List<SettingsEditorFragment<T, ?>> createRunFragments() {
    List<SettingsEditorFragment<T, ?>> fragments = new ArrayList<>();
    BeforeRunComponent beforeRunComponent = new BeforeRunComponent(this);
    fragments.add(BeforeRunFragment.createBeforeRun(beforeRunComponent, CompileStepBeforeRun.ID));
    fragments.addAll(BeforeRunFragment.createGroup());

    SettingsEditorFragment<T, ModuleClasspathCombo> moduleClasspath = createClasspathCombo();
    ModuleClasspathCombo classpathCombo = moduleClasspath.component();
    Computable<Boolean> hasModule = () -> classpathCombo.getSelectedModule() != null;

    fragments.add(CommonTags.parallelRun());

    CommonParameterFragments<T> commonParameterFragments = new CommonParameterFragments<>(myProject, hasModule);
    fragments.addAll(commonParameterFragments.getFragments());
    fragments.add(CommonJavaFragments.createBuildBeforeRun(beforeRunComponent));

    String group = ExecutionBundle.message("group.java.options");
    RawCommandLineEditor vmOptions = new RawCommandLineEditor();
    setMinimumWidth(vmOptions, 400);
    String message = ExecutionBundle.message("run.configuration.java.vm.parameters.empty.text");
    vmOptions.getEditorField().getAccessibleContext().setAccessibleName(message);
    vmOptions.getEditorField().getEmptyText().setText(message);
    MacrosDialog.addMacroSupport(vmOptions.getEditorField(), MacrosDialog.Filters.ALL, hasModule);
    FragmentedSettingsUtil.setupPlaceholderVisibility(vmOptions.getEditorField());
    SettingsEditorFragment<T, RawCommandLineEditor> vmParameters =
      new SettingsEditorFragment<>("vmParameters", ExecutionBundle.message("run.configuration.java.vm.parameters.name"), group, vmOptions,
                                   15,
                                   (configuration, c) -> c.setText(configuration.getVMParameters()),
                                   (configuration, c) -> configuration.setVMParameters(c.isVisible() ? c.getText() : null),
                                   configuration -> isNotEmpty(configuration.getVMParameters()));
    vmParameters.setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"));
    vmParameters.setEditorGetter(editor -> editor.getEditorField());
    fragments.add(vmParameters);
    fragments.add(moduleClasspath);
    customizeFragments(fragments, classpathCombo);

    fragments.add(new LogsFragment<>());
    return fragments;
  }

  @NotNull
  protected abstract SettingsEditorFragment<T, ModuleClasspathCombo> createClasspathCombo();

  @NotNull
  protected SettingsEditorFragment<T, LabeledComponent<ShortenCommandLineModeCombo>> createShortenClasspath(ModuleClasspathCombo classpathCombo,
                                                                                                          SettingsEditorFragment<T, JrePathEditor> jrePath) {
    ShortenCommandLineModeCombo combo =
      new ShortenCommandLineModeCombo(myProject, jrePath.component(), () -> classpathCombo.getSelectedModule(),
                                      listener -> classpathCombo.addActionListener(listener));
    LabeledComponent<ShortenCommandLineModeCombo> component = LabeledComponent.create(combo,
                                                                                      ExecutionBundle.message("application.configuration.shorten.command.line.label"),
                                                                                      BorderLayout.WEST);
    return new SettingsEditorFragment<>("shorten.command.line",
                                        ExecutionBundle.message("application.configuration.shorten.command.line"),
                                        ExecutionBundle.message("group.java.options"), component,
                                        (configuration, c) -> c
                                          .getComponent().setItem(configuration.getShortenCommandLine()),
                                        (configuration, c) -> configuration.setShortenCommandLine(
                                            c.isVisible() ? c.getComponent().getSelectedItem(): null),
                                        configuration -> configuration.getShortenCommandLine() != null);
  }

  protected abstract void customizeFragments(List<SettingsEditorFragment<T, ?>> fragments, ModuleClasspathCombo classpathCombo);
}
