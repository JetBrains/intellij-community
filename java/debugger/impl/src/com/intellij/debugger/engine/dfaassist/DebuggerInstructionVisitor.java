// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.StandardInstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

class DebuggerInstructionVisitor extends StandardInstructionVisitor {
  private final Map<PsiExpression, DfaHint> myHints = new HashMap<>();

  DebuggerInstructionVisitor() {
    super(true);
  }

  private void addHint(@NotNull PsiExpression expression, @Nullable DfaHint hint) {
    if (hint != null) {
      myHints.merge(expression, hint, DfaHint::merge);
    }
  }

  @Override
  protected void beforeExpressionPush(@NotNull DfaValue value,
                                      @NotNull PsiExpression expression,
                                      @Nullable TextRange range,
                                      @NotNull DfaMemoryState state) {
    if (range != null) return;
    DfaHint hint = DfaHint.ANY_VALUE;
    if (value instanceof DfaConstValue) {
      Object constVal = ((DfaConstValue)value).getValue();
      if (Boolean.TRUE.equals(constVal)) {
        hint = DfaHint.TRUE;
      }
      else if (Boolean.FALSE.equals(constVal)) {
        hint = DfaHint.FALSE;
      }
      else if (DfaConstValue.isContractFail(value)) {
        hint = DfaHint.FAIL;
      }
    }
    addHint(expression, hint);
  }

  @Override
  protected void onTypeCast(PsiTypeCastExpression castExpression, DfaMemoryState state, boolean castPossible) {
    if (!castPossible) {
      addHint(castExpression, DfaHint.CCE);
    }
    super.onTypeCast(castExpression, state, castPossible);
  }

  @Override
  protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {
    if (alwaysOutOfBounds) {
      addHint(expression, DfaHint.AIOOBE);
    }
    super.processArrayAccess(expression, alwaysOutOfBounds);
  }

  @Override
  protected void processArrayStoreTypeMismatch(PsiAssignmentExpression assignmentExpression, PsiType fromType, PsiType toType) {
    addHint(assignmentExpression.getLExpression(), DfaHint.ASE);
    super.processArrayStoreTypeMismatch(assignmentExpression, fromType, toType);
  }

  @Override
  protected boolean checkNotNullable(DfaMemoryState state,
                                     DfaValue value,
                                     @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      PsiExpression expression = problem.getDereferencedExpression();
      if (expression != null && problem.thrownException() != null) {
        if (state.isNull(value)) {
          DfaHint hint = problem.thrownException().equals(CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION)
                         ? DfaHint.NPE
                         : DfaHint.NULL_AS_NOT_NULL;
          addHint(expression, hint);
        }
      }
    }
    return super.checkNotNullable(state, value, problem);
  }
  
  void cleanup() {
    myHints.values().removeIf(h -> h.getTitle() == null);
    myHints.keySet().removeIf(expr -> {
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expr);
      return result != null && result.getExpressionValues(expr).size() == 1;
    });
  }
  
  @NotNull
  Map<PsiExpression, DfaHint> getHints() {
    return myHints;
  } 
}
