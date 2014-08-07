/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public abstract static class ProblemListener {
    public void problemsAppeared(@NotNull VirtualFile file) {}
    public void problemsChanged(@NotNull VirtualFile file) {}
    public void problemsDisappeared(@NotNull VirtualFile file) {}
  }

  public abstract void addProblemListener(@NotNull ProblemListener listener);
  public abstract void addProblemListener(@NotNull ProblemListener listener, @NotNull Disposable parentDisposable);
  public abstract void removeProblemListener(@NotNull ProblemListener listener);

  /**
   * @deprecated register extensions to {@link #FILTER_EP_NAME} instead
   */
  public abstract void registerFileHighlightFilter(@NotNull Condition<VirtualFile> filter, @NotNull Disposable parentDisposable);
  public abstract void queue(VirtualFile suspiciousFile);
}
