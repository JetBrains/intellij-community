// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.MoveClassesOrPackagesRefactoring;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class MoveClassesOrPackagesRefactoringImpl extends RefactoringImpl<MoveClassesOrPackagesProcessor> implements MoveClassesOrPackagesRefactoring {


  public MoveClassesOrPackagesRefactoringImpl(Project project,
                                              PsiElement[] elements,
                                              MoveDestination moveDestination, 
                                              boolean searchInComments, 
                                              boolean searchInNonJavaFiles) {
    super(new MoveClassesOrPackagesProcessor(project, elements, moveDestination, searchInComments, searchInNonJavaFiles, null));
  }

  @Override
  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }

  @Override
  public PackageWrapper getTargetPackage() {
    return myProcessor.getTargetPackage();
  }

  @Override
  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  @Override
  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchInNonJavaFiles(value);
  }

  @Override
  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  @Override
  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchInNonJavaFiles();
  }
}
