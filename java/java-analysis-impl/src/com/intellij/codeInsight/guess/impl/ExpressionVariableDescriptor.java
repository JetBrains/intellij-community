// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ExpressionVariableDescriptor implements VariableDescriptor {
  public static final Hash.Strategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new PsiExpressionStrategy();
  
  private final @NotNull PsiExpression myExpression;

  public ExpressionVariableDescriptor(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  @Override
  public boolean isStable() {
    return true;
  }

  public @NotNull PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  public @Nullable PsiType getType(@Nullable DfaVariableValue qualifier) {
    return myExpression.getType();
  }

  @Override
  public int hashCode() {
    return EXPRESSION_HASHING_STRATEGY.hashCode(myExpression);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ExpressionVariableDescriptor && 
           EXPRESSION_HASHING_STRATEGY.equals(myExpression, ((ExpressionVariableDescriptor)obj).myExpression);
  }

  @Override
  public String toString() {
    return myExpression.getText();
  }

  private static class PsiExpressionStrategy implements Hash.Strategy<PsiExpression> {
    private static final Logger LOG = Logger.getInstance(PsiExpressionStrategy.class);

    @Override
    public int hashCode(PsiExpression object) {
      if (object == null) {
        return 0;
      }
      else if (object instanceof PsiReferenceExpression) {
        return Objects.hashCode(((PsiReferenceExpression)object).getReferenceName()) * 31 + 1;
      }
      else if (object instanceof PsiMethodCallExpression) {
        return Objects.hashCode(((PsiMethodCallExpression)object).getMethodExpression().getReferenceName()) * 31 + 2;
      }
      return object.getNode().getElementType().hashCode();
    }

    @Override
    public boolean equals(PsiExpression o1, PsiExpression o2) {
      if (o1 == o2) {
        return true;
      }
      if (o1 == null || o2 == null) {
        return false;
      }
      if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(o1, o2)) {
        if (hashCode(o1) != hashCode(o2)) {
          LOG.error("different hashCodes: " + o1 + "; " + o2 + "; " + hashCode(o1) + "!=" + hashCode(o2));
        }
        return true;
      }
      return false;
    }
  }
}
