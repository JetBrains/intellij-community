// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.SafeDeleteRefactoring;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public final class SafeDeleteRefactoringImpl extends RefactoringImpl<SafeDeleteProcessor> implements SafeDeleteRefactoring {
  public SafeDeleteRefactoringImpl(Project project, PsiElement[] elements) {
    super(SafeDeleteProcessor.createInstance(project, EmptyRunnable.INSTANCE, elements, true, true));
  }

  @Override
  public List<PsiElement> getElements() {
    final PsiElement[] elements = myProcessor.getElements();
    return ContainerUtil.immutableList(elements);
  }

  @Override
  public boolean isSearchInComments() {
    return myProcessor.isSearchInCommentsAndStrings();
  }

  @Override
  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInCommentsAndStrings(value);
  }

  @Override
  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchNonJava(value);
  }

  @Override
  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchNonJava();
  }
}
