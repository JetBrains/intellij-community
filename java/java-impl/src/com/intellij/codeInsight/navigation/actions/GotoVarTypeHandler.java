// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

public final class GotoVarTypeHandler extends GotoDeclarationHandlerBase {
  @Override
  @Nullable
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiKeyword && PsiKeyword.VAR.equals(elementAt.getText())) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiTypeElement && ((PsiTypeElement)parent).isInferredType()) {
        return PsiUtil.resolveClassInClassTypeOnly(((PsiTypeElement)parent).getType());
      }
    }
    return null;
  }
}
