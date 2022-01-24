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

  @Override
  public PsiClass getInnerClass() {
    return myProcessor.getInnerClass();
  }

  @Override
  public String getNewClassName() {
    return myProcessor.getNewClassName();
  }

  @Override
  public boolean shouldPassParameter() {
    return myProcessor.shouldPassParameter();
  }

  @Override
  public String getParameterName() {
    return myProcessor.getParameterName();
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
