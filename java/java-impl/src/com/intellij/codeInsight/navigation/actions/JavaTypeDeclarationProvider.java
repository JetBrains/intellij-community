// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class JavaTypeDeclarationProvider implements TypeDeclarationPlaceAwareProvider {
  @Override
  public PsiElement[] getSymbolTypeDeclarations(@NotNull PsiElement symbol) {
    return getSymbolTypeDeclarations(symbol, null, -1);
  }

  @Override
  public PsiElement @Nullable [] getSymbolTypeDeclarations(@NotNull PsiElement targetElement, Editor editor, int offset) {
    PsiType type;
    if (targetElement instanceof PsiVariable){
      type = ((PsiVariable)targetElement).getType();
    }
    else if (targetElement instanceof PsiMethod){
      type = ((PsiMethod)targetElement).getReturnType();
    }
    else{
      return null;
    }
    if (type == null) return null;
    if (editor != null) {
      final PsiReference reference = TargetElementUtil.findReference(editor, offset);
      if (reference instanceof PsiJavaReference) {
        final JavaResolveResult resolveResult = ((PsiJavaReference)reference).advancedResolve(true);
        type = resolveResult.getSubstitutor().substitute(type);
      }
    }
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[] {psiClass};
  }
}
