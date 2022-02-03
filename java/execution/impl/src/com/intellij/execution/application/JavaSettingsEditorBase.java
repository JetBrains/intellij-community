// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.diagnostic.logging.LogsGroupFragment;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.ui.*;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class JavaSettingsEditorBase<T extends JavaRunConfigurationBase> extends RunConfigurationFragmentedEditor<T> {
  public JavaSettingsEditorBase(T runConfiguration) {
    super(runConfiguration, JavaRunConfigurationExtensionManager.getInstance());
  }

  @Override
  protected List<SettingsEditorFragment<T, ?>> createRunFragments() {
    List<SettingsEditorFragment<T, ?>> fragments = new ArrayList<>();
    BeforeRunComponent beforeRunComponent = new BeforeRunComponent(this);
    fragments.add(BeforeRunFragment.createBeforeRun(beforeRunComponent, CompileStepBeforeRun.ID));
    fragments.addAll(BeforeRunFragment.createGroup());

    SettingsEditorFragment<T, ModuleClasspathCombo> moduleClasspath = CommonJavaFragments.moduleClasspath();
    ModuleClasspathCombo classpathCombo = moduleClasspath.component();
    Computable<Boolean> hasModule = () -> classpathCombo.getSelectedModule() != null;

    fragments.add(CommonTags.parallelRun());

    CommonParameterFragments<T> commonParameterFragments = new CommonParameterFragments<>(getProject(), () -> classpathCombo.getSelectedModule());
    fragments.addAll(commonParameterFragments.getFragments());
    fragments.add(CommonJavaFragments.createBuildBeforeRun(beforeRunComponent, this));

    String group = ExecutionBundle.message("group.java.options");
    RawCommandLineEditor vmOptions = new RawCommandLineEditor();
    CommandLinePanel.setMinimumWidth(vmOptions, 400);
    CommonParameterFragments.setMonospaced(vmOptions.getTextField());
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
                                   configuration -> StringUtil.isNotEmpty(configuration.getVMParameters()));
    vmParameters.setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"));
    vmParameters.setActionHint(ExecutionBundle.message("specify.vm.options.for.running.the.application"));
    vmParameters.setEditorGetter(editor -> editor.getEditorField());
    fragments.add(vmParameters);
    fragments.add(moduleClasspath);
    fragments.add(new ClasspathModifier<>(mySettings));
    customizeFragments(fragments, moduleClasspath, commonParameterFragments);

    fragments.add(new LogsGroupFragment<>());
    return fragments;
  }

  @NotNull
  protected SettingsEditorFragment<T, LabeledComponent<ShortenCommandLineModeCombo>> createShortenClasspath(ModuleClasspathCombo classpathCombo,
                                                                                                            SettingsEditorFragment<T, JrePathEditor> jrePath,
                                                                                                            boolean productionOnly) {
    ShortenCommandLineModeCombo combo = new ShortenCommandLineModeCombo(getProject(), jrePath.component(),
                                                                        () -> classpathCombo.getSelectedModule(),
                                                                        listener -> classpathCombo.addActionListener(listener)) {
      @Override
      protected boolean productionOnly() {
        return productionOnly;
      }
    };
    LabeledComponent<ShortenCommandLineModeCombo> component = LabeledComponent.create(combo,
                                                                                      ExecutionBundle.message(
                                                                                        "application.configuration.shorten.command.line.label"),
                                                                                      BorderLayout.WEST);
    SettingsEditorFragment<T, LabeledComponent<ShortenCommandLineModeCombo>> fragment =
      new SettingsEditorFragment<>("shorten.command.line",
                                   ExecutionBundle.message("application.configuration.shorten.command.line"),
                                   ExecutionBundle.message("group.java.options"),
                                   component,
                                   (t, c) -> c.getComponent().setItem(t.getShortenCommandLine()),
                                   (t, c) -> t.setShortenCommandLine(c.isVisible() ? c.getComponent().getSelectedItem() : null),
                                   configuration -> configuration.getShortenCommandLine() != null);
    fragment.setActionHint(ExecutionBundle.message("select.a.method.to.shorten.the.command.if.it.exceeds.the.os.limit"));
    return fragment;
  }

  protected abstract void customizeFragments(List<SettingsEditorFragment<T, ?>> fragments,
                                             SettingsEditorFragment<T, ModuleClasspathCombo> moduleClasspath,
                                             CommonParameterFragments<T> commonParameterFragments);

  @Override
  public void targetChanged(String targetName) {
    super.targetChanged(targetName);
    SettingsEditorFragment<T, ?> fragment = ContainerUtil.find(getFragments(), f -> CommonJavaFragments.JRE_PATH == f.getId());
    if (fragment != null) {
      if (((JrePathEditor)fragment.component()).updateModel(getProject(), targetName)) {
        fragment.resetFrom(mySettings);
      }
    }
  }
}
