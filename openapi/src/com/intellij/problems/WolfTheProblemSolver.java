/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public abstract class WolfTheProblemSolver implements ProjectComponent {
  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  public abstract boolean isProblemFile(VirtualFile virtualFile);

  public abstract void weHaveGotProblem(Problem problem);
  public abstract void weHaveGotProblems(@NotNull VirtualFile virtualFile, @NotNull List<Problem> problems);
  public abstract void clearProblems(@NotNull VirtualFile virtualFile);

  public abstract boolean hasProblemFilesBeneath(Condition<VirtualFile> condition);

  public abstract boolean hasProblemFilesBeneath(PsiElement scope);

  public abstract boolean hasProblemFilesBeneath(Module scope);

  @Nullable
  public abstract Problem convertToProblem(CompilerMessage message);
  public abstract Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message);

  public abstract void reportProblems(final VirtualFile file, Collection<Problem> problems);

  public abstract boolean hasSyntaxErrors(final VirtualFile file);

  public abstract static class ProblemListener {
    public void problemsAppeared(VirtualFile file) {}
    public void problemsChanged(VirtualFile file) {}
    public void problemsDisappeared(VirtualFile file) {}
  }

  public abstract void addProblemListener(ProblemListener listener);
  public abstract void addProblemListener(ProblemListener listener, Disposable parentDisposable);
  public abstract void removeProblemListener(ProblemListener listener);

  public abstract void registerFileHighlightFilter(Condition<VirtualFile> filter, Disposable parentDisposable);
}
