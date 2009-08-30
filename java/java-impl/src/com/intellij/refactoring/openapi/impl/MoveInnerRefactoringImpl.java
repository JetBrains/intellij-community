/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.MoveInnerRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;

/**
 * @author dsl
 */
public class MoveInnerRefactoringImpl extends RefactoringImpl<MoveInnerProcessor> implements MoveInnerRefactoring {
  public MoveInnerRefactoringImpl(Project project,
                                  PsiClass innerClass,
                                  String name,
                                  boolean passOuterClass,
                                  String parameterName,
                                  final PsiElement targetContainer) {
    super(new MoveInnerProcessor(project, innerClass, name, passOuterClass, parameterName, targetContainer));
  }

  public PsiClass getInnerClass() {
    return myProcessor.getInnerClass();
  }

  public String getNewClassName() {
    return myProcessor.getNewClassName();
  }

  public boolean shouldPassParameter() {
    return myProcessor.shouldPassParameter();
  }

  public String getParameterName() {
    return myProcessor.getParameterName();
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchInNonJavaFiles(value);
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchInNonJavaFiles();
  }
}
