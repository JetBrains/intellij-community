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
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.TypeCookRefactoring;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.TypeCookProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class TypeCookRefactoringImpl extends RefactoringImpl<TypeCookProcessor> implements TypeCookRefactoring {
  TypeCookRefactoringImpl(Project project,
                          PsiElement[] elements,
                          final boolean dropObsoleteCasts,
                          final boolean leaveObjectsRaw,
                          final boolean preserveRawArrays,
                          final boolean exhaustiveSearch,
                          final boolean cookObjects,
                          final boolean cookToWildcards) {
    super(new TypeCookProcessor(project, elements, new Settings() {
      @Override
      public boolean dropObsoleteCasts() {
        return dropObsoleteCasts;
      }

      @Override
      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectsRaw;
      }

      @Override
      public boolean exhaustive() {
        return exhaustiveSearch;
      }

      @Override
      public boolean cookObjects() {
        return cookObjects;
      }

      @Override
      public boolean cookToWildcards() {
        return cookToWildcards;
      }

      @Override
      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }
    }));
  }

  @Override
  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }
}
