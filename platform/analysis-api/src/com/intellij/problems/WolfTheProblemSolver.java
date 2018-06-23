// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

/**
 * @author cdr
 */
public abstract class WolfTheProblemSolver {
  public static final ExtensionPointName<Condition<VirtualFile>> FILTER_EP_NAME = ExtensionPointName.create("com.intellij.problemFileHighlightFilter");

  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  public abstract boolean isProblemFile(VirtualFile virtualFile);

  public abstract void weHaveGotProblems(@NotNull VirtualFile virtualFile, @NotNull List<Problem> problems);
  public abstract void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<Problem> problems);
  public abstract void clearProblems(@NotNull VirtualFile virtualFile);

  public abstract boolean hasProblemFilesBeneath(@NotNull Condition<VirtualFile> condition);

  public abstract boolean hasProblemFilesBeneath(@NotNull Module scope);

  public abstract Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message);

  public abstract void reportProblems(final VirtualFile file, Collection<Problem> problems);

  public abstract boolean hasSyntaxErrors(final VirtualFile file);

  @Deprecated
  public abstract static class ProblemListener implements com.intellij.problems.ProblemListener {
    public void problemsAppeared(@NotNull VirtualFile file) {}

    public void problemsChanged(@NotNull VirtualFile file) {}

    public void problemsDisappeared(@NotNull VirtualFile file) {}
  }

  /**
   * @deprecated Use message bus {@link ProblemListener#TOPIC} instead.
   */
  @Deprecated
  public abstract void addProblemListener(@NotNull ProblemListener listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated register extensions to {@link #FILTER_EP_NAME} instead
   */
  @Deprecated
  public abstract void registerFileHighlightFilter(@NotNull Condition<VirtualFile> filter, @NotNull Disposable parentDisposable);
  public abstract void queue(VirtualFile suspiciousFile);
}
