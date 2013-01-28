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
package com.intellij.refactoring.actions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperHandler;
import org.jetbrains.annotations.NotNull;

public class TurnRefsToSuperAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass && elements[0].getLanguage() == JavaLanguage.INSTANCE;
  }

  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new TurnRefsToSuperHandler();
  }
}