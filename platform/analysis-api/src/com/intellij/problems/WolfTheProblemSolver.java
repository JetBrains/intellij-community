// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.problems;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class WolfTheProblemSolver {
  protected static final ExtensionPointName<Condition<VirtualFile>> FILTER_EP_NAME = new ExtensionPointName<>("com.intellij.problemFileHighlightFilter");

  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getService(WolfTheProblemSolver.class);
  }

  public abstract boolean isProblemFile(@NotNull VirtualFile virtualFile);

  public abstract void weHaveGotProblems(@NotNull VirtualFile virtualFile, @NotNull List<? extends Problem> problems);
  public abstract void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<? extends Problem> problems);
  public abstract void clearProblems(@NotNull VirtualFile virtualFile);

  public abstract boolean hasProblemFilesBeneath(@NotNull Condition<? super VirtualFile> condition);

  public abstract boolean hasProblemFilesBeneath(@NotNull Module scope);

  public abstract Problem convertToProblem(@NotNull VirtualFile virtualFile, int line, int column, String @NotNull [] message);

  public abstract void reportProblems(@NotNull VirtualFile file, @NotNull Collection<? extends Problem> problems);

  public abstract boolean hasSyntaxErrors(@NotNull VirtualFile file);

  /**
   * Reports that the specified file contains problems that cannot be discovered by running the general
   * highlighting pass for the file.
   *
   * @param source Identifies the component that discovered the problems. A file is highlighted as problematic
   *               if it has problems from GeneralHighlightingPass or from at least one source.
   */
  public abstract void reportProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source);

  /**
   * Reports that the specified file no longer contains problems discovered by the specified source. If the
   * file has no problems from GeneralHighlightingPass or from any other sources, it will no longer be
   * highlighted as problematic.
   */
  public abstract void clearProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source);

  /**
   * @deprecated use {@link com.intellij.problems.ProblemListener} directly
   */
  @Deprecated(forRemoval = true)
  public abstract static class ProblemListener implements com.intellij.problems.ProblemListener {
    @Override
    public void problemsAppeared(@NotNull VirtualFile file) { com.intellij.problems.ProblemListener.super.problemsAppeared(file); }

    @Override
    public void problemsDisappeared(@NotNull VirtualFile file) { com.intellij.problems.ProblemListener.super.problemsDisappeared(file); }
  }

  /**
   * @deprecated Use message bus {@link ProblemListener#TOPIC} instead.
   */
  @Deprecated(forRemoval = true)
  public abstract void addProblemListener(@NotNull ProblemListener listener, @NotNull Disposable parentDisposable);

  public abstract void queue(@NotNull VirtualFile suspiciousFile);
}
