// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ExpectedTypesGetter {

  public static PsiType @NotNull [] getExpectedTypes(final PsiElement context, boolean defaultTypes) {
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if(expression == null) return PsiType.EMPTY_ARRAY;

    return extractTypes(ExpectedTypesProvider.getExpectedTypes(expression, true), defaultTypes);
  }

  public static PsiType @NotNull [] extractTypes(ExpectedTypeInfo[] infos, boolean defaultTypes) {
    Set<PsiType> result = new THashSet<>(infos.length);
    for (ExpectedTypeInfo info : infos) {
      final PsiType type = info.getType();
      final PsiType defaultType = info.getDefaultType();
      if (!defaultTypes && !defaultType.equals(type)) {
        result.add(type);
      }
      result.add(defaultType);
    }
    return result.toArray(PsiType.createArray(result.size()));
  }
}
