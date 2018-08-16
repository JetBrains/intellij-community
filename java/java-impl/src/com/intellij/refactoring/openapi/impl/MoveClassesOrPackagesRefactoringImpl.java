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


  public MoveClassesOrPackagesRefactoringImpl(Project project, PsiElement[] elements, MoveDestination moveDestination) {
    super(new MoveClassesOrPackagesProcessor(project, elements, moveDestination, true, true, null));
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
