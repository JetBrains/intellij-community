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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;
import org.jetbrains.annotations.NotNull;

public class EncapsulateFieldsAction extends BaseJavaRefactoringAction {
  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file,
                                                        @NotNull DataContext context, @NotNull String place) {
    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
    if (containingClass != null) {
      if (ActionPlaces.isPopupPlace(place) || place.equals(ActionPlaces.REFACTORING_QUICKLIST)) {
        if (PsiTreeUtil.getParentOfType(psiElement, PsiField.class, false) == null) return false;
      }
      final PsiField[] fields = containingClass.getFields();
      for (PsiField field : fields) {
        if (isAcceptedField(field)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass && elements[0].getLanguage().isKindOf(JavaLanguage.INSTANCE) || isAcceptedField(elements[0]);
    }
    else if (elements.length > 1) {
      for (PsiElement element : elements) {
        if (!isAcceptedField(element)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new EncapsulateFieldsHandler();
  }

  private static boolean isAcceptedField(PsiElement element) {
    return element instanceof PsiField &&
           element.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
           ((PsiField)element).getContainingClass() != null;
  }
}