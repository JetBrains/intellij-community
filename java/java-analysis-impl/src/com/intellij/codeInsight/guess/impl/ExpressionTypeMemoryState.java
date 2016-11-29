/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.value.DfaInstanceofValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public class ExpressionTypeMemoryState extends DfaMemoryStateImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.guess.impl.ExpressionTypeMemoryState");
  public static final TObjectHashingStrategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new TObjectHashingStrategy<PsiExpression>() {
    @Override
    public int computeHashCode(PsiExpression object) {
      return object.getNode().getElementType().hashCode();
    }

    @Override
    public boolean equals(PsiExpression o1, PsiExpression o2) {
      if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(o1, o2)) {
        if (computeHashCode(o1) != computeHashCode(o2)) {
          LOG.error("different hashCodes: " + o1 + "; " + o2 + "; " + computeHashCode(o1) + "!=" + computeHashCode(o2));
        }

        return true;
      }
      return false;
    }
  };
  private final Map<PsiExpression, PsiType> myStates = new THashMap<>(EXPRESSION_HASHING_STRATEGY);

  public ExpressionTypeMemoryState(final DfaValueFactory factory) {
    super(factory);
  }

  private ExpressionTypeMemoryState(DfaMemoryStateImpl toCopy) {
    super(toCopy);
  }

  @NotNull
  @Override
  public DfaMemoryStateImpl createCopy() {
    final ExpressionTypeMemoryState copy = new ExpressionTypeMemoryState(this);
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
    return super.toString() + " states=[" + new HashMap<>(myStates) + "]";
  }

  public void setExpressionType(PsiExpression expression, @NotNull PsiType type) {
    PsiType prev = myStates.get(expression);
    if (prev == null || !type.isAssignableFrom(prev)) {
      myStates.put(expression, type);
    }
  }
}
