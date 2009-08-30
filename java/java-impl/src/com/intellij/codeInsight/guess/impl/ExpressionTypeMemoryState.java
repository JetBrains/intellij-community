/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Map;

/**
 * @author peter
 */
public class ExpressionTypeMemoryState extends DfaMemoryStateImpl {
  public static final TObjectHashingStrategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new TObjectHashingStrategy<PsiExpression>() {
    public int computeHashCode(PsiExpression object) {
      return object.getNode().getElementType().hashCode();
    }

    public boolean equals(PsiExpression o1, PsiExpression o2) {
      return CodeInsightUtil.areExpressionsEquivalent(o1, o2);
    }
  };
  private final Map<PsiExpression, PsiType> myStates = new THashMap<PsiExpression, PsiType>(EXPRESSION_HASHING_STRATEGY);

  public ExpressionTypeMemoryState(final DfaValueFactory factory) {
    super(factory);
  }

  @Override
  protected DfaMemoryStateImpl createNew() {
    return new ExpressionTypeMemoryState(getFactory());
  }

  @Override
  public DfaMemoryStateImpl createCopy() {
    final ExpressionTypeMemoryState copy = (ExpressionTypeMemoryState)super.createCopy();
    copy.myStates.putAll(myStates);
    return copy;
  }

  @Override
  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaInstanceofValue) {
      final DfaInstanceofValue value = (DfaInstanceofValue)dfaCond;
      if (!value.isNegated()) {
        setExpressionType(value.getExpression(), value.getCastType());
      }
    }

    return super.applyCondition(dfaCond);
  }

  public Map<PsiExpression, PsiType> getStates() {
    return myStates;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ExpressionTypeMemoryState that = (ExpressionTypeMemoryState)o;

    if (!myStates.equals(that.myStates)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myStates.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return super.toString() + " states=[" + new HashMap<PsiExpression, PsiType>(myStates) + "]";
  }

  public void setExpressionType(PsiExpression expression, PsiType type) {
    myStates.put(expression, type);
  }
}
