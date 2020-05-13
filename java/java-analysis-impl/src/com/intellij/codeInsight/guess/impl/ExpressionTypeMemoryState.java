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
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * @author peter
 */
public class ExpressionTypeMemoryState extends DfaMemoryStateImpl {
  private static final Logger LOG = Logger.getInstance(ExpressionTypeMemoryState.class);
  public static final TObjectHashingStrategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new TObjectHashingStrategy<PsiExpression>() {
    @Override
    public int computeHashCode(PsiExpression object) {
      if (object instanceof PsiReferenceExpression) {
        return Objects.hashCode(((PsiReferenceExpression)object).getReferenceName()) * 31 + 1;
      }
      else if (object instanceof PsiMethodCallExpression) {
        return Objects.hashCode(((PsiMethodCallExpression)object).getMethodExpression().getReferenceName()) * 31 + 2;
      }
      return object.getNode().getElementType().hashCode();
    }

    @Override
    public boolean equals(@NotNull PsiExpression o1, @NotNull PsiExpression o2) {
      if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(o1, o2)) {
        if (computeHashCode(o1) != computeHashCode(o2)) {
          LOG.error("different hashCodes: " + o1 + "; " + o2 + "; " + computeHashCode(o1) + "!=" + computeHashCode(o2));
        }

        return true;
      }
      return false;
    }
  };
  private final boolean myHonorAssignments;
  // may be shared between memory state instances
  private MultiMap<PsiExpression, PsiType> myStates;

  public ExpressionTypeMemoryState(final DfaValueFactory factory, boolean honorAssignments) {
    super(factory);
    myHonorAssignments = honorAssignments;
    myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
  }

  private ExpressionTypeMemoryState(ExpressionTypeMemoryState toCopy) {
    super(toCopy);
    myHonorAssignments = toCopy.myHonorAssignments;
    myStates = toCopy.myStates;
  }

  @Override
  protected DfType filterDfTypeOnAssignment(DfaVariableValue var, @NotNull DfType dfType) {
    if (myHonorAssignments) return dfType;
    if (ControlFlowAnalyzer.isTempVariable(var) || (!(dfType instanceof DfReferenceType)) ||    
        var.getPsiVariable() instanceof PsiParameter && var.getPsiVariable().getParent().getParent() instanceof PsiLambdaExpression) {
      // Pass type normally for synthetic lambda parameter assignment
      return dfType;
    }
    return ((DfReferenceType)dfType).dropTypeConstraint();
  }

  @NotNull
  @Override
  public DfaMemoryStateImpl createCopy() {
    return new ExpressionTypeMemoryState(this);
  }

  @Override
  public boolean isSuperStateOf(DfaMemoryStateImpl that) {
    if (!super.isSuperStateOf(that)) {
      return false;
    }
    MultiMap<PsiExpression, PsiType> thatStates = ((ExpressionTypeMemoryState)that).myStates;
    if (thatStates == myStates) return true;
    for (Map.Entry<PsiExpression, Collection<PsiType>> entry : myStates.entrySet()) {
      Collection<PsiType> thisTypes = entry.getValue();
      Collection<PsiType> thatTypes = thatStates.get(entry.getKey());
      if (!thatTypes.containsAll(thisTypes)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean applyCondition(DfaCondition dfaCond) {
    if (dfaCond instanceof DfaRelation) {
      DfaRelation rel = (DfaRelation)dfaCond;
      DfaValue leftOperand = rel.getLeftOperand();
      DfaValue rightOperand = rel.getRightOperand();
      RelationType relation = rel.getRelation();
      if (leftOperand instanceof DfaInstanceofValue && (relation == RelationType.EQ || relation == RelationType.NE)) {
        DfaInstanceofValue value = (DfaInstanceofValue)leftOperand;
        Boolean val = DfConstantType.getConstantOfType(rightOperand.getDfType(), Boolean.class);
        if (val != null) {
          boolean negated = (relation == RelationType.EQ) != val; 
          if (!negated) {
            setExpressionType(value.getExpression(), value.getCastType());
          }
          return super.applyCondition(negated ? value.getRelation().negate() : value.getRelation());
        }
      }
    }

    return super.applyCondition(dfaCond);
  }

  MultiMap<PsiExpression, PsiType> getStates() {
    return myStates;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ExpressionTypeMemoryState that = (ExpressionTypeMemoryState)o;
    return myStates.equals(that.myStates);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myStates.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return super.toString() + " states=[" + myStates + "]";
  }

  void removeExpressionType(@NotNull PsiExpression expression) {
    if (myStates.containsKey(expression)) {
      MultiMap<PsiExpression, PsiType> oldStates = myStates;
      myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
      for (Map.Entry<PsiExpression, Collection<PsiType>> entry : oldStates.entrySet()) {
        if(!EXPRESSION_HASHING_STRATEGY.equals(entry.getKey(), expression)) {
          myStates.putValues(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  void setExpressionType(@NotNull PsiExpression expression, @NotNull PsiType type) {
    if (!myStates.get(expression).contains(type)) {
      MultiMap<PsiExpression, PsiType> oldStates = myStates;
      myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
      myStates.putAllValues(oldStates);
      myStates.putValue(expression, type);
    }
  }
}
