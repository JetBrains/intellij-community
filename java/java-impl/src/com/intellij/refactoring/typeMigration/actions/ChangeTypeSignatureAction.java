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
package com.intellij.refactoring.typeMigration.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.typeMigration.ChangeTypeSignatureHandler;
import org.jetbrains.annotations.NotNull;

public class ChangeTypeSignatureAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    Project currProject = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());

    if (currProject == null) {
      return false;
    }

    if (elements.length > 1) return false;

    for (PsiElement element : elements) {
      if (!(element instanceof PsiMethod || element instanceof PsiVariable)) {
        return false;
      }
    }

    return true;
  }

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull final PsiElement element, @NotNull final Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement psiElement = file.findElementAt(offset);
    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(psiElement, PsiReferenceParameterList.class);
    if (referenceParameterList != null) {
      return referenceParameterList.getTypeArguments().length > 0;
    }
    return PsiTreeUtil.getParentOfType(psiElement, PsiTypeElement.class) != null;
  }

  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ChangeTypeSignatureHandler();
  }
}
