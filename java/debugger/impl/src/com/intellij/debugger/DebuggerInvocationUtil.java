/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DebuggerInvocationUtil {
  public static void swingInvokeLater(@Nullable final Project project, @NotNull final Runnable runnable) {
    if (project == null) {
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!project.isDisposed()) {
          runnable.run();
        }
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

  public static void invokeAndWait(final Project project, @NotNull final Runnable runnable, ModalityState state) {
    if (project != null) {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          if (!project.isDisposed()) {
            runnable.run();
          }
        }
      }, state);
    }
  }

  public static <T> T commitAndRunReadAction(Project project, final EvaluatingComputable<T> computable) throws EvaluateException {
    final Throwable[] ex = new Throwable[]{null};
    T result = PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Computable<T>() {
      @Override
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