// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;

public abstract class JavaPatchableProgramRunner<Settings extends RunnerSettings> extends GenericProgramRunner<Settings> {
  public abstract void patch(JavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, final boolean beforeExecution) throws ExecutionException;


  protected static void runCustomPatchers(JavaParameters javaParameters,
                                          Executor executor,
                                          RunProfile runProfile) {
    if (runProfile != null) {
      for (JavaProgramPatcher patcher : JavaProgramPatcher.EP_NAME.getExtensionList()) {
        patcher.patchJavaParameters(executor, runProfile, javaParameters);
      }
    }
  }
}
