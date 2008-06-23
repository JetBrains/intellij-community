package com.intellij.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerInvocationUtil {
  public static void invokeLater(final Project project, @NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (project != null && !project.isDisposed()) {
          runnable.run();
        }
      }
    });
  }

  public static void invokeLater(final Project project, @NotNull final Runnable runnable, ModalityState state) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if(project == null || project.isDisposed()) return;

        runnable.run();
      }
    }, state);
  }

  public static void invokeAndWait(final Project project, @NotNull final Runnable runnable, ModalityState state) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        if(project == null || project.isDisposed()) return;

        runnable.run();
      }
    }, state);
  }

  public static  <T> T commitAndRunReadAction(Project project, final EvaluatingComputable<T> computable) throws EvaluateException {
    final Throwable[] ex = new Throwable[] { null };
    T result = PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Computable<T>() {
          public T compute() {
            try {
              return computable.compute();
            }
            catch (RuntimeException e) {
              ex[0] = e;
            }
            catch (Exception th) {
              ex[0] = th;
            }

            return null;
          }
        });

    if(ex[0] != null) {
      if(ex[0] instanceof RuntimeException) {
        throw (RuntimeException)ex[0];
      }
      else {
        throw (EvaluateException) ex[0];
      }
    }

    return result;
  }
}
