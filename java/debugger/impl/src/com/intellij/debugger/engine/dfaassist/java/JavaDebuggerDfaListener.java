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

  @Override
  public @NotNull Collection<TextRange> unreachableSegments(@NotNull PsiElement startAnchor, @NotNull Set<PsiElement> unreachable) {
    Set<TextRange> result = new HashSet<>();
    for (PsiElement element : unreachable) {
      ContainerUtil.addIfNotNull(result, createRange(startAnchor, element, unreachable));
    }
    return result;
  }

  private static @Nullable TextRange createRange(@NotNull PsiElement startAnchor,
                                                 @NotNull PsiElement unreachable,
                                                 @NotNull Set<PsiElement> allUnreachable) {
    PsiElement parent = unreachable.getParent();
    if (unreachable instanceof PsiExpression expression) {
      if (parent instanceof PsiConditionalExpression && !allUnreachable.contains(parent)) {
        return expression.getTextRange();
      }
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
          PsiExpression prev = PsiTreeUtil.getPrevSiblingOfType(expression, PsiExpression.class);
          if (prev != null && !allUnreachable.contains(prev)) {
            PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
            if (token != null) {
              return TextRange.create(token.getTextRange().getStartOffset(), parent.getTextRange().getEndOffset());
            }
          }
        }
      }
    }
    if (unreachable instanceof PsiCodeBlock) {
      if (unreachable.getParent() instanceof PsiCatchSection && unreachable.getParent().getParent() instanceof PsiTryStatement &&
          !allUnreachable.contains(unreachable.getParent().getParent())) {
        return unreachable.getTextRange();
      }
    }
    if (unreachable instanceof PsiStatement statement) {
      if (ControlFlowUtils.isEmpty(unreachable, false, true)) return null;
      PsiElement statementParent = statement.getParent();
      if (unreachable instanceof PsiSwitchLabelStatement) return null;
      if (allUnreachable.contains(statementParent)) return null;
      if (parent instanceof PsiStatement) {
        if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() == unreachable) {
          PsiKeyword elseKeyword = ((PsiIfStatement)parent).getElseElement();
          if (elseKeyword != null) {
            return TextRange.create(elseKeyword.getTextRange().getStartOffset(), unreachable.getTextRange().getEndOffset());
          }
        }
        return statement.getTextRange();
      }
      if (parent instanceof PsiCodeBlock) {
        if (statement instanceof PsiSwitchLabeledRuleStatement) {
          PsiSwitchBlock block = ((PsiSwitchLabeledRuleStatement)statement).getEnclosingSwitchBlock();
          if (!allUnreachable.contains(block)) {
            PsiStatement body = ((PsiSwitchLabeledRuleStatement)statement).getBody();
            if (body != null) {
              return body.getTextRange();
            }
          }
          return null;
        }
        PsiStatement prevStatement = ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement), PsiStatement.class);
        if (prevStatement instanceof PsiSwitchLabelStatement) {
          PsiSwitchBlock block = ((PsiSwitchLabelStatement)prevStatement).getEnclosingSwitchBlock();
          if (block != null && !allUnreachable.contains(block)) {
            PsiElement last = ((PsiCodeBlock)statementParent).getRBrace();
            PsiSwitchLabelStatement nextLabel = PsiTreeUtil.getNextSiblingOfType(statement, PsiSwitchLabelStatement.class);
            if (nextLabel != null) {
              last = nextLabel;
            }
            PsiElement lastStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(last);
            if (lastStatement != null) {
              return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
            }
          }
          return null;
        }
        if (allUnreachable.contains(prevStatement)) return null;
        PsiElement lastStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(((PsiCodeBlock)statementParent).getRBrace());
        if (lastStatement != null && prevStatement != null) {
          if (prevStatement instanceof PsiLoopStatement && PsiTreeUtil.isAncestor(prevStatement, startAnchor, false)) {
            return null;
          }
          return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
        }
      }
    }
    return null;
  }
}
