// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RunConfigurationExtension extends RunConfigurationExtensionBase<RunConfigurationBase<?>>{
  public static final ExtensionPointName<RunConfigurationExtension> EP_NAME =
    new ExtensionPointName<>("com.intellij.runConfigurationExtension");

  @ApiStatus.Experimental
  public <T extends RunConfigurationBase> void updateJavaParameters(@NotNull final T configuration,
                                                                    @NotNull final JavaParameters params,
                                                                    RunnerSettings runnerSettings,
                                                                    @NotNull final Executor executor) throws ExecutionException {
    updateJavaParameters(configuration, params, runnerSettings);
  }

  public abstract <T extends RunConfigurationBase> void updateJavaParameters(@NotNull final T configuration,
                                                                             @NotNull final JavaParameters params, RunnerSettings runnerSettings) throws ExecutionException;

  @Override
  protected void patchCommandLine(@NotNull RunConfigurationBase configuration,
                                  RunnerSettings runnerSettings,
                                  @NotNull GeneralCommandLine cmdLine,
                                  @NotNull String runnerId)  throws ExecutionException {}

  @Override
  public boolean isEnabledFor(@NotNull RunConfigurationBase applicableConfiguration, @Nullable RunnerSettings runnerSettings) {
    return true;
  }

  public void cleanUserData(RunConfigurationBase runConfigurationBase) {}

  public static void cleanExtensionsUserData(RunConfigurationBase runConfigurationBase) {
    for (RunConfigurationExtension extension : EP_NAME.getExtensionList()) {
      extension.cleanUserData(runConfigurationBase);
    }
  }

  public RefactoringElementListener wrapElementListener(PsiElement element,
                                                        RunConfigurationBase runJavaConfiguration,
                                                        RefactoringElementListener listener) {
    return listener;
  }

  public static RefactoringElementListener wrapRefactoringElementListener(PsiElement element,
                                                                          RunConfigurationBase runConfigurationBase,
                                                                          RefactoringElementListener listener) {
    for (RunConfigurationExtension extension : EP_NAME.getExtensionList()) {
      listener = extension.wrapElementListener(element, runConfigurationBase, listener);
    }
    return listener;
  }

  public  boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
    return false;
  }
}