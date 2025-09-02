// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.getters;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class InstanceOfLeftPartTypeGetter {
  public static PsiType @NotNull [] getLeftTypes(PsiElement context) {
    if((context = FilterUtil.getPreviousElement(context, true)) == null) return PsiType.EMPTY_ARRAY;
    if(!JavaKeywords.INSTANCEOF.equals(context.getText())) return PsiType.EMPTY_ARRAY;
    if((context = FilterUtil.getPreviousElement(context, false)) == null) return PsiType.EMPTY_ARRAY;

    final PsiExpression contextOfType = PsiTreeUtil.getContextOfType(context, PsiExpression.class, false);
    if (contextOfType == null) return PsiType.EMPTY_ARRAY;

    PsiType type = contextOfType.getType();
    if (type == null) return PsiType.EMPTY_ARRAY;

    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass instanceof PsiTypeParameter) {
        return psiClass.getExtendsListTypes();
      }
    }

    return new PsiType[]{type};
  }
}
