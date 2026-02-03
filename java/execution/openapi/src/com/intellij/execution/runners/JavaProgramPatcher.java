// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Patch Java command line before running/debugging
 */
public abstract class JavaProgramPatcher {
  protected static final ExtensionPointName<JavaProgramPatcher> EP_NAME = ExtensionPointName.create("com.intellij.java.programPatcher");

  public abstract void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters);

  public static void runCustomPatchers(@NotNull JavaParameters javaParameters, @NotNull Executor executor, @NotNull RunProfile runProfile) {
    EP_NAME.forEachExtensionSafe(patcher -> {
      ReadAction.run(() -> patcher.patchJavaParameters(executor, runProfile, javaParameters));
    });
  }

  /**
   * Wrap patchers in progress
   */
  public static boolean patchJavaCommandLineParamsUnderProgress(@NotNull Project project,
                                                                ThrowableRunnable<? extends ExecutionException> patch) throws ExecutionException {
    AtomicReference<ExecutionException> ex = new AtomicReference<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        patch.run();
      }
      catch (ProcessCanceledException ignore) {}
      catch (ExecutionException e) {
        ex.set(e);
      }
    }, ExecutionBundle.message("progress.title.patch.java.command.line.parameters"), true, project)) {
      return false;
    }
    if (ex.get() != null) throw ex.get();
    return true;
  }
}
