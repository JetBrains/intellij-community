/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TypeCookAction extends BaseRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isAvailableForLanguage(Language language) {
    return language.equals(JavaLanguage.INSTANCE);
  }

  @Override
  public boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return elements.length > 0 && Arrays.stream(elements).allMatch(
      e -> e instanceof PsiClass || e instanceof PsiJavaFile || e instanceof PsiDirectory || e instanceof PsiPackage);
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return getHandler();
  }

  public RefactoringActionHandler getHandler() {
    return new TypeCookHandler();
  }
}