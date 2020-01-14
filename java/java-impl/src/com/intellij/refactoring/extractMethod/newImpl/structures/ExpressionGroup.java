// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;

import java.util.List;

public class ExpressionGroup {
  public final PsiType type;
  public final List<PsiExpression> references;

  private ExpressionGroup(PsiType type, List<PsiExpression> expressions) {
    this.type = type;
    this.references = expressions;
  }

  public static ExpressionGroup of(List<PsiExpression> expressions){
    if (expressions.isEmpty()) throw new IllegalArgumentException("Expression group is empty");
    final PsiType type = expressions.get(0).getType();
    if (type == null) throw new IllegalArgumentException("Some expressions have null psi type");
    if (! expressions.stream().allMatch(it -> type.equals(it.getType()))) throw new IllegalArgumentException("Some expressions have different type");
    return new ExpressionGroup(type, expressions);
  }

}
