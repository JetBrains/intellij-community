// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;

public class BuildViewFactoryImpl implements BuildViewFactory {
  private final Project myProject;

  public BuildViewFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  public BuildProgressListener createBuildView(ExternalSystemTaskId id,
                                               String executionName,
                                               String workingDir,
                                               ExecutionConsole executionConsole,
                                               ProcessHandler processHandler) {
    BuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, executionName, workingDir, System.currentTimeMillis());
      return new BuildView(myProject, executionConsole, buildDescriptor, "build.toolwindow.run.selection.state",
                           new ViewManager() {
                             @Override
                             public boolean isConsoleEnabledByDefault() {
                               return true;
                             }

                             @Override
                             public boolean isBuildContentView() {
                               return false;
                             }
                           });
  }
}
