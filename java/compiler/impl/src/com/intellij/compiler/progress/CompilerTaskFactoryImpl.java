// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
