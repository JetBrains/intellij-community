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

package com.intellij.codeInsight.problems;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  private WolfTheProblemSolver myDelegate;

  @Override
  public boolean isProblemFile(VirtualFile virtualFile) {
    return myDelegate != null && myDelegate.isProblemFile(virtualFile);
  }

  @Override
  public void weHaveGotProblems(@NotNull final VirtualFile virtualFile, @NotNull final List<Problem> problems) {
    if (myDelegate != null) myDelegate.weHaveGotProblems(virtualFile, problems);
  }

  @Override
  public void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<Problem> problems) {
    if (myDelegate != null) myDelegate.weHaveGotNonIgnorableProblems(virtualFile, problems);
  }

  @Override
  public boolean hasProblemFilesBeneath(@NotNull final Condition<VirtualFile> condition) {
    return false;
  }

  @Override
  public boolean hasSyntaxErrors(final VirtualFile file) {
    return myDelegate != null && myDelegate.hasSyntaxErrors(file);
  }

  @Override
  public boolean hasProblemFilesBeneath(@NotNull Module scope) {
    return false;
  }

  @Override
  public void addProblemListener(@NotNull ProblemListener listener) {
    if (myDelegate != null) myDelegate.addProblemListener(listener);
  }

  @Override
  public void addProblemListener(@NotNull ProblemListener listener, @NotNull Disposable parentDisposable) {
    if (myDelegate != null) myDelegate.addProblemListener(listener, parentDisposable);
  }

  @Override
  public void removeProblemListener(@NotNull ProblemListener listener) {
    if (myDelegate != null) myDelegate.removeProblemListener(listener);
  }

  @Override
  public void registerFileHighlightFilter(@NotNull Condition<VirtualFile> filter, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void queue(VirtualFile suspiciousFile) {
    if (myDelegate != null) myDelegate.queue(suspiciousFile);
  }

  @Override
  public void clearProblems(@NotNull VirtualFile virtualFile) {
    if (myDelegate != null) myDelegate.clearProblems(virtualFile);
  }

  @Override
  public Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message) {
    return myDelegate == null ? null : myDelegate.convertToProblem(virtualFile, line, column, message);
  }

  public void setDelegate(final WolfTheProblemSolver delegate) {
    myDelegate = delegate;
  }

  @Override
  public void reportProblems(final VirtualFile file, Collection<Problem> problems) {
    if (myDelegate != null) myDelegate.reportProblems(file,problems);
  }
}
