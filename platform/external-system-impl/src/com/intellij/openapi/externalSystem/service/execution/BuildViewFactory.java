// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildProgressListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;

public interface BuildViewFactory {
  static BuildViewFactory getInstance(Project project) {
    return ServiceManager.getService(project, BuildViewFactory.class);
  }

  BuildProgressListener createBuildView(ExternalSystemTaskId id,
                                        String name,
                                        String dir,
                                        ExecutionConsole console,
                                        ProcessHandler processHandler);
}
