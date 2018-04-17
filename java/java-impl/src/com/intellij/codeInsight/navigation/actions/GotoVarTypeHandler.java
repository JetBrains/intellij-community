// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

public class GotoVarTypeHandler extends GotoDeclarationHandlerBase {
  @Override
  @Nullable
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiIdentifier) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiVariable) {
        PsiTypeElement typeElement = ((PsiVariable)parent).getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          return parent;
        }
      }
    }
    else if (elementAt instanceof PsiKeyword && PsiKeyword.VAR.equals(elementAt.getText())) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiTypeElement && ((PsiTypeElement)parent).isInferredType()) {
        return PsiUtil.resolveClassInClassTypeOnly(((PsiTypeElement)parent).getType());
      }
    }
    return null;
  }
}
