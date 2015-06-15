/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    return (element instanceof PsiMethod &&
            ((PsiMethod)element).isConstructor() &&
            acceptClass(((PsiMethod)element).getContainingClass())  ||
            acceptClass(element))
           && element.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  private static boolean acceptClass(PsiElement element) {
    return element instanceof PsiClass && !((PsiClass)element).isEnum();
  }

  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ReplaceConstructorWithFactoryHandler();
  }
}
