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
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.ReplaceConstructorWithFactoryRefactoring;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryRefactoringImpl extends RefactoringImpl<ReplaceConstructorWithFactoryProcessor> implements ReplaceConstructorWithFactoryRefactoring {
  ReplaceConstructorWithFactoryRefactoringImpl(Project project, PsiMethod method, PsiClass targetClass, String factoryName) {
    super(new ReplaceConstructorWithFactoryProcessor(project, method, null, targetClass, factoryName));
  }

  ReplaceConstructorWithFactoryRefactoringImpl(Project project, PsiClass originalClass, PsiClass targetClass, String factoryName) {
    super(new ReplaceConstructorWithFactoryProcessor(project, null, originalClass, targetClass, factoryName));
  }

  @Override
  public PsiClass getOriginalClass() {
    return myProcessor.getOriginalClass();
  }

  @Override
  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }

  @Override
  public PsiMethod getConstructor() {
    return myProcessor.getConstructor();
  }

  @Override
  public String getFactoryName() {
    return myProcessor.getFactoryName();
  }

}
