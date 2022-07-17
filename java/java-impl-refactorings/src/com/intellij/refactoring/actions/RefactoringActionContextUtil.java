// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class RefactoringActionContextUtil {
  public static boolean isJavaClassHeader(@Nullable PsiElement element) {
    if (element == null) return false;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    return psiClass != null && (element == psiClass || element == psiClass.getNameIdentifier() ||
                                PsiTreeUtil.isAncestor(psiClass.getModifierList(), element, false) ||
                                PsiTreeUtil.isAncestor(psiClass.getExtendsList(), element, false) ||
                                PsiTreeUtil.isAncestor(psiClass.getImplementsList(), element, false));
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

  public static boolean isOutsideModuleAndCodeBlock(@NotNull Editor editor,
                                                    @NotNull PsiFile file) {
    if (PsiUtil.isModuleFile(file)) {
      return false;
    }
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class) == null;
  }

  public static boolean isClassWithExtendsOrImplements(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    return isNotEmpty(psiClass.getExtendsList()) || isNotEmpty(psiClass.getImplementsList());
  }

  private static boolean isNotEmpty(@Nullable PsiReferenceList referenceList){
    return referenceList != null && referenceList.getReferenceElements().length > 0;
  }
}
