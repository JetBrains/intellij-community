package com.intellij.compiler.progress;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CompilerTaskFactoryImpl implements CompilerTaskFactory {
  private Project myProject;

  public CompilerTaskFactoryImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public CompilerTaskBase createCompilerTask(String contentName,
                                             final boolean headlessMode,
                                             boolean forceAsync,
                                             boolean waitForPreviousSession,
                                             boolean compilationStartedAutomatically,
                                             boolean modal) {
    return new CompilerTask(myProject, contentName, headlessMode, forceAsync, waitForPreviousSession, compilationStartedAutomatically, modal);
  }
}
