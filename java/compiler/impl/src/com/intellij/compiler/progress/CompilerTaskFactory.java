package com.intellij.compiler.progress;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface CompilerTaskFactory {

  class SERVICE {
    public static CompilerTaskFactory getInstance(@NotNull Project project) {
      return ServiceManager.getService(project, CompilerTaskFactory.class);
    }
  }

  @NotNull
  CompilerTaskBase createCompilerTask(String contentName,
                                      boolean headlessMode,
                                      boolean forceAsync,
                                      boolean waitForPreviousSession,
                                      boolean compilationStartedAutomatically, boolean modal);
}