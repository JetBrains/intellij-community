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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import org.jetbrains.annotations.NotNull;

public class AnonymousToInnerAction extends BaseJavaRefactoringAction {
  @Override
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull final PsiElement element, @NotNull final Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final PsiElement targetElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (PsiTreeUtil.getParentOfType(targetElement, PsiAnonymousClass.class) != null) {
      return true;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class) != null) {
      return true;
    }
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    return newExpression != null && newExpression.getAnonymousClass() != null;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new AnonymousToInnerHandler();
  }
}
