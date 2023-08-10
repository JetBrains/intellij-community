// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * This class exists for compatibility only.
 * Will be removed together with {@link PsiSwitchLabelStatementBase#getCaseValues()} in the future.
 */
public class LightExpressionList extends LightElement implements PsiExpressionList {
  private final PsiExpression[] myExpressions;
  private final @NotNull PsiElement myContext;
  private final TextRange myRange;

  public LightExpressionList(@NotNull PsiManager manager,
                             @NotNull Language language,
                             PsiExpression[] expressions,
                             @NotNull PsiElement context,
                             TextRange range) {
    super(manager, language);
    myExpressions = expressions;
    myRange = range;
    myContext = context;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiExpression @NotNull [] getExpressions() {
    return myExpressions;
  }

  @Override
  public PsiType @NotNull [] getExpressionTypes() {
    PsiExpression[] expressions = getExpressions();
    PsiType[] types = PsiType.createArray(expressions.length);

    for (int i = 0; i < types.length; i++) {
      types[i] = expressions[i].getType();
    }

    return types;
  }

  @Override
  public TextRange getTextRange() {
    return myRange;
  }

  @Override
  public @NotNull PsiElement getContext() {
    return myContext;
  }

  @Override
  public String toString() {
    return "PsiExpressionList";
  }
}
