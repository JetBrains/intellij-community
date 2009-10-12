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

package com.intellij.codeInsight.problems;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  private WolfTheProblemSolver myDelegate;

  public boolean isProblemFile(VirtualFile virtualFile) {
    return myDelegate != null && myDelegate.isProblemFile(virtualFile);
  }

  public void weHaveGotProblems(@NotNull final VirtualFile virtualFile, @NotNull final List<Problem> problems) {
    if (myDelegate != null) myDelegate.weHaveGotProblems(virtualFile, problems);
  }

  public boolean hasProblemFilesBeneath(final Condition<VirtualFile> condition) {
    return false;
  }

  public boolean hasSyntaxErrors(final VirtualFile file) {
    return myDelegate != null && myDelegate.hasSyntaxErrors(file);
  }

  public boolean hasProblemFilesBeneath(Module scope) {
    return false;
  }

  public void addProblemListener(ProblemListener listener) {
    if (myDelegate != null) myDelegate.addProblemListener(listener);
  }

  public void addProblemListener(ProblemListener listener, Disposable parentDisposable) {
    if (myDelegate != null) myDelegate.addProblemListener(listener, parentDisposable);
  }

  public void removeProblemListener(ProblemListener listener) {
    if (myDelegate != null) myDelegate.removeProblemListener(listener);
  }

  public void registerFileHighlightFilter(Condition<VirtualFile> filter, Disposable parentDisposable) {
  }

  @Override
  public void queue(VirtualFile suspiciousFile) {
    if (myDelegate != null) myDelegate.queue(suspiciousFile);
  }

  public void projectOpened() {
    if (myDelegate != null) myDelegate.projectOpened();
  }

  public void projectClosed() {
    if (myDelegate != null) myDelegate.projectClosed();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "mockwolf";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void clearProblems(@NotNull VirtualFile virtualFile) {
    if (myDelegate != null) myDelegate.clearProblems(virtualFile);
  }

  public Problem convertToProblem(final VirtualFile virtualFile, final HighlightSeverity severity, final TextRange textRange,
                                  final String messageText) {
    return myDelegate == null ? null : myDelegate.convertToProblem(virtualFile, severity, textRange, messageText);
  }

  public Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message) {
    return myDelegate == null ? null : myDelegate.convertToProblem(virtualFile, line, column, message);
  }

  public void setDelegate(final WolfTheProblemSolver delegate) {
    myDelegate = delegate;
  }

  public void reportProblems(final VirtualFile file, Collection<Problem> problems) {
    if (myDelegate != null) myDelegate.reportProblems(file,problems);
  }
}
