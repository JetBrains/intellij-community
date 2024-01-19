// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist.java;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ClassCastProblem;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener;
import com.intellij.xdebugger.impl.dfaassist.DfaHint;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class JavaDebuggerDfaListener implements JavaDfaListener, DebuggerDfaListener {
  private static final TokenSet BOOLEAN_TOKENS = TokenSet.create(
    JavaTokenType.ANDAND, JavaTokenType.OROR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.EQEQ, JavaTokenType.NE);

  private final Map<PsiElement, DfaHint> myHints = new HashMap<>();

  private void addHint(@NotNull PsiElement element, @Nullable DfaHint hint) {
    if (hint != null) {
      myHints.merge(element, hint, DfaHint::merge);
    }
  }

  @Override
  public void beforeExpressionPush(@NotNull DfaValue value,
                                   @NotNull PsiExpression expression,
                                   @NotNull DfaMemoryState state) {
    if (!shouldTrackExpressionValue(expression)) return;
    DfaHint hint = DfaHint.ANY_VALUE;
    DfType dfType = state.getDfType(value);
    if (dfType == DfTypes.TRUE) {
      hint = DfaHint.TRUE;
    }
    else if (dfType == DfTypes.FALSE) {
      hint = DfaHint.FALSE;
    }
    else if (dfType == DfType.FAIL) {
      hint = DfaHint.FAIL;
    }
    addHint(expression, hint);
  }

  @Override
  public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                          @NotNull DfaValue value,
                          @NotNull ThreeState failed,
                          @NotNull DfaMemoryState state) {
    if (problem instanceof ArrayStoreProblem) {
      addHint(((ArrayStoreProblem)problem).getAnchor().getLExpression(), failed == ThreeState.YES ? DfaHint.ASE : DfaHint.NONE);
    }
    else if (problem instanceof ArrayIndexProblem) {
      PsiArrayAccessExpression anchor = ((ArrayIndexProblem)problem).getAnchor();
      // Anchor to the last child to differentiate from ArrayStoreException
      addHint(anchor.getLastChild(), failed == ThreeState.YES ? DfaHint.AIOOBE : DfaHint.NONE);
    }
    else if (problem instanceof ClassCastProblem) {
      addHint(((ClassCastProblem)problem).getAnchor(), failed == ThreeState.YES ? DfaHint.CCE : DfaHint.NONE);
    }
    else if (problem instanceof NullabilityProblemKind.NullabilityProblem<?> npeProblem) {
      PsiExpression expression = npeProblem.getDereferencedExpression();
      if (expression != null && npeProblem.thrownException() != null) {
        DfaHint hint;
        if (failed == ThreeState.YES) {
          hint = npeProblem.thrownException().equals(CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION)
                 ? DfaHint.NPE
                 : DfaHint.NULL_AS_NOT_NULL;
        }
        else {
          hint = DfaHint.NONE;
        }
        addHint(expression, hint);
      }
    }
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
        if (firstOperand != null && PsiTypes.booleanType().equals(firstOperand.getType())) {
          // For polyadic boolean expression let's report components only, otherwise the report gets cluttered
          return false;
        }
      }
    }
    return true;
  }

  void cleanup() {
    myHints.entrySet().removeIf(e -> {
      PsiElement anchor = e.getKey();
      DfaHint hint = e.getValue();
      if (hint.getTitle() == null) return true;
      if (!(anchor instanceof PsiExpression expr)) return false;
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expr);
      return result != null && result.getExpressionValues(expr).size() == 1;
    });
  }

  @Override
  public @NotNull Map<PsiElement, DfaHint> computeHints() {
    cleanup();
    return Collections.unmodifiableMap(myHints);
  }
}
