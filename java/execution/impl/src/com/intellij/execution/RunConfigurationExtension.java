// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows changing behaviour for already created {@link RunConfigurationBase}
 */
public abstract class RunConfigurationExtension extends RunConfigurationExtensionBase<RunConfigurationBase<?>> {
  public static final ExtensionPointName<RunConfigurationExtension> EP_NAME =
    new ExtensionPointName<>("com.intellij.runConfigurationExtension");

  public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration,
                                                                       @NotNull JavaParameters params,
                                                                       RunnerSettings runnerSettings,
                                                                       @NotNull Executor executor) throws ExecutionException {
    updateJavaParameters(configuration, params, runnerSettings);
  }

  /**
   * @param params java parameters to be updated. E.g. put additional jars on classpath or module path, additional VM options, etc
   */
  public abstract <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration,
                                                                                @NotNull JavaParameters params,
                                                                                @Nullable RunnerSettings runnerSettings) throws ExecutionException;

  @Override
  protected void patchCommandLine(@NotNull RunConfigurationBase configuration,
                                  RunnerSettings runnerSettings,
                                  @NotNull GeneralCommandLine cmdLine,
                                  @NotNull String runnerId) throws ExecutionException { }

  @Override
  public boolean isEnabledFor(@NotNull RunConfigurationBase applicableConfiguration, @Nullable RunnerSettings runnerSettings) {
    return true;
  }

  public void cleanUserData(RunConfigurationBase<?> runConfigurationBase) { }

  public static void cleanExtensionsUserData(@NotNull RunConfigurationBase<?> runConfigurationBase) {
    for (RunConfigurationExtension extension : EP_NAME.getExtensionList()) {
      extension.cleanUserData(runConfigurationBase);
    }
  }

  /**
   * Allows updating custom settings for existing configuration when {@code element} is moved or renamed.
   */
  public RefactoringElementListener wrapElementListener(PsiElement element,
                                                        RunConfigurationBase<?> runJavaConfiguration,
                                                        RefactoringElementListener listener) {
    return listener;
  }

  public static RefactoringElementListener wrapRefactoringElementListener(PsiElement element,
                                                                          RunConfigurationBase<?> runConfigurationBase,
                                                                          RefactoringElementListener listener) {
    for (RunConfigurationExtension extension : EP_NAME.getExtensionList()) {
      listener = extension.wrapElementListener(element, runConfigurationBase, listener);
    }
    return listener;
  }

  /**
   * @return true if {@code runnerSettings} explicitly says that current extension should be disabled,
   * e.g. for run without coverage, coverage listeners should not be enabled
   */
  public boolean isListenerDisabled(RunConfigurationBase<?> configuration, Object listener, RunnerSettings runnerSettings) {
    return false;
  }

  /**
   * Enhances the run process console by adding any extension-specific information to it.
   */
  @NotNull
  protected ConsoleView decorate(@NotNull ConsoleView console, @NotNull RunConfigurationBase<?> configuration, @NotNull Executor executor) {
    return console;
  }
}