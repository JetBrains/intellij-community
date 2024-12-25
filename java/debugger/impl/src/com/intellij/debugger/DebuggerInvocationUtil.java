// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class DebuggerInvocationUtil {
  public static void swingInvokeLater(final @Nullable Project project, final @NotNull Runnable runnable) {
    if (project == null) {
      return;
    }

    SwingUtilities.invokeLater(() -> {
      if (!project.isDisposed()) {
        ReadAction.run(() -> runnable.run());
      }
    });
  }

  public static void invokeLater(@Nullable Project project, @NotNull Runnable runnable) {
    if (project != null) {
      ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
    }
  }

  public static void invokeLater(@Nullable Project project, @NotNull Runnable runnable, ModalityState state) {
    if (project != null) {
      ApplicationManager.getApplication().invokeLater(runnable, state, project.getDisposed());
    }
  }

  public static void invokeAndWait(final Project project, final @NotNull Runnable runnable, ModalityState state) {
    if (project != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (!project.isDisposed()) {
          runnable.run();
        }
      }, state);
    }
  }

  public static <T> T commitAndRunReadAction(Project project, final EvaluatingComputable<T> computable) throws EvaluateException {
    final Throwable[] ex = new Throwable[]{null};
    T result = PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
      try {
        return computable.compute();
      }
      catch (RuntimeException | EvaluateException e) {
        ex[0] = e;
      }

      return null;
    });

    if (ex[0] != null) {
      if (ex[0] instanceof RuntimeException) {
        throw (RuntimeException)ex[0];
      }
      else {
        throw (EvaluateException)ex[0];
      }
    }

    return result;
  }
}