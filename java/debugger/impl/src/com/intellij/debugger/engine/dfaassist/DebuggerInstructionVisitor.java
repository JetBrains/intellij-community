// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.StandardInstructionVisitor;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

class DebuggerInstructionVisitor extends StandardInstructionVisitor {
  private static final TokenSet BOOLEAN_TOKENS = TokenSet.create(
    JavaTokenType.ANDAND, JavaTokenType.OROR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.EQEQ, JavaTokenType.NE);
  
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
    if (range != null || !shouldTrackExpressionValue(expression)) return;
    DfaHint hint = DfaHint.ANY_VALUE;
    DfType dfType = state.getDfType(value);
    if (dfType == DfTypes.TRUE) {
      hint = DfaHint.TRUE;
    }
    else if (dfType == DfTypes.FALSE) {
      hint = DfaHint.FALSE;
    }
    else if (dfType == DfTypes.FAIL) {
      hint = DfaHint.FAIL;
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
  protected ThreeState checkNotNullable(DfaMemoryState state,
                                        @NotNull DfaValue value,
                                        @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      PsiExpression expression = problem.getDereferencedExpression();
      if (expression != null && problem.thrownException() != null) {
        DfaHint hint;
        if (state.isNull(value)) {
          hint = problem.thrownException().equals(CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION)
                 ? DfaHint.NPE
                 : DfaHint.NULL_AS_NOT_NULL;
        } else {
          hint = DfaHint.NONE;
        }
        addHint(expression, hint);
      }
    }
    return super.checkNotNullable(state, value, problem);
  }
  
  private static boolean shouldTrackExpressionValue(@NotNull PsiExpression expr) {
    if (BoolUtils.isNegated(expr)) {
      // It's enough to report for parent only
      return false;
    }
    if (expr instanceof PsiAssignmentExpression) {
      // Report right hand of assignment only
      return false;
    }
    if (expr instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)expr).getOperationTokenType();
      if (BOOLEAN_TOKENS.contains(tokenType)) {
        PsiExpression firstOperand = ((PsiPolyadicExpression)expr).getOperands()[0];
        if (firstOperand != null && PsiType.BOOLEAN.equals(firstOperand.getType())) {
          // For polyadic boolean expression let's report components only, otherwise the report gets cluttered
          return false;
        }
      }
    }
    return true;
  }
  
  void cleanup() {
    myHints.entrySet().removeIf(e -> {
      PsiExpression expr = e.getKey();
      DfaHint hint = e.getValue();
      if (hint.getTitle() == null) return true;
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expr);
      return result != null && result.getExpressionValues(expr).size() == 1;
    });
  }
  
  @NotNull
  Map<PsiExpression, DfaHint> getHints() {
    return myHints;
  } 
}
