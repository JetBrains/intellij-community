// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class JavaDataFlowIRProvider implements DataFlowIRProvider {
  @Override
  public @Nullable ControlFlow createControlFlow(@NotNull DfaValueFactory factory, @NotNull PsiElement psiBlock) {
    return new ControlFlowAnalyzer(factory, psiBlock, true).buildControlFlow();
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
