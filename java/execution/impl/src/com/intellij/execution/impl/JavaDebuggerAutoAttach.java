// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.impl.attach.JavaAttachDebuggerProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
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
    if (Registry.is("debugger.auto.attach.from.console")) {
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          Matcher matcher = JavaDebuggerConsoleFilterProvider.getConnectionMatcher(event.getText());
          if (matcher != null) {
            String transport = matcher.group(1);
            String address = matcher.group(2);
            Project project = configuration.getProject();

            JavaAttachDebuggerProvider.attach(transport, address, null, project);
          }
        }
      });
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
    return true;
  }
}
