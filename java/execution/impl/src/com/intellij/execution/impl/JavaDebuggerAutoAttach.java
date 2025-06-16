// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.impl.attach.JavaAttachDebuggerProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

public final class JavaDebuggerAutoAttach extends RunConfigurationExtension {
  @Override
  public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration,
                                                                       @NotNull JavaParameters params,
                                                                       RunnerSettings runnerSettings) throws ExecutionException {
  }

  @Override
  protected void attachToProcess(@NotNull RunConfigurationBase<?> configuration,
                                 @NotNull ProcessHandler handler,
                                 @Nullable RunnerSettings runnerSettings) {
    if (Registry.is("debugger.auto.attach.from.console") && !Registry.is("debugger.auto.attach.from.any.console")) {
      handler.addProcessListener(new ProcessListener() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          Matcher matcher = JavaDebuggerConsoleFilterProvider.getConnectionMatcher(event.getText());
          if (matcher != null) {
            String transport = matcher.group(1);
            String address = matcher.group(2);
            Project project = configuration.getProject();

            ApplicationManager.getApplication().invokeLater(
              () -> JavaAttachDebuggerProvider.attach(transport, address, null, project),
              ModalityState.any());
          }
        }
      });
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
    return EelPathUtils.isProjectLocal(configuration.getProject());
  }
}
