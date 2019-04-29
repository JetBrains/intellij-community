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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConstructorWithBuilderAction extends BaseJavaRefactoringAction{
  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file,
                                                        @NotNull DataContext context, @NotNull String place) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(offset);
    final PsiClass psiClass = ReplaceConstructorWithBuilderHandler.getParentNamedClass(elementAt);
    if (psiClass == null || psiClass.getConstructors().length == 0 || psiClass.isEnum()) {
      return false;
    }
    if (ActionPlaces.isPopupPlace(place) || place.equals(ActionPlaces.REFACTORING_QUICKLIST)) {
      PsiMethod method = getJavaMethodHeader(elementAt);
      return method != null && method.isConstructor();
    }
    return true;
  }

  @Nullable
  public static PsiMethod getJavaMethodHeader(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return null;
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && (element == psiMethod || element == psiMethod.getNameIdentifier() ||
                                 PsiTreeUtil.isAncestor(psiMethod.getModifierList(), element, false) ||
                                 PsiTreeUtil.isAncestor(psiMethod.getParameterList(), element, false))) {
      return psiMethod;
    }
    return null;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull final PsiElement[] elements) {
    return false;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull final DataContext dataContext) {
    return new ReplaceConstructorWithBuilderHandler();
  }
}
