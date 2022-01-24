// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class BooleanReturnModel {
  private final boolean myEarlyReturnValue;
  private final int myEarlyReturnValueCount;
  private final int myTerminalNonRemovableValueCount;
  private final boolean myHasReturnInLoopOrSwitch;

  BooleanReturnModel(boolean value, int count, int terminalNonRemovableValueCount, boolean hasReturnInLoopOrSwitch) {
    myEarlyReturnValue = value;
    myEarlyReturnValueCount = count;
    myTerminalNonRemovableValueCount = terminalNonRemovableValueCount;
    myHasReturnInLoopOrSwitch = hasReturnInLoopOrSwitch;
  }

  @Nullable
  InlineTransformer getTransformer(PsiReference ref) {
    if (!(ref instanceof PsiReferenceExpression)) return null;
    PsiMethodCallExpression call = ObjectUtils.tryCast(((PsiReferenceExpression)ref).getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    boolean wantedValue = true;
    PsiElement parent;
    PsiExpression expression = call;
    while (true) {
      parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      if (parent instanceof PsiPrefixExpression && JavaTokenType.EXCL.equals(((PsiPrefixExpression)parent).getOperationTokenType())) {
        wantedValue = !wantedValue;
        expression = (PsiPrefixExpression)parent;
      }
      else {
        break;
      }
    }
    if (parent instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)parent;
      PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      PsiStatement elseStatement = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
      if (elseStatement == null && wantedValue == myEarlyReturnValue) {
        boolean allowReplacement;
        if (myEarlyReturnValueCount == 1 && myTerminalNonRemovableValueCount == 0) {
          allowReplacement = isJumpOut(thenStatement);
        }
        else {
          allowReplacement = isCopyableJumpOut(thenStatement);
        }
        if (allowReplacement) {
          return getTransformer(thenStatement, null);
        }
      }
      if (isJumpOut(thenStatement) && isJumpOut(elseStatement) && myTerminalNonRemovableValueCount == 0 && myEarlyReturnValueCount == 1) {
        PsiStatement earlyStatement = myEarlyReturnValue == wantedValue ? thenStatement : elseStatement;
        PsiStatement finalStatement = myEarlyReturnValue != wantedValue ? thenStatement : elseStatement;
        return getTransformer(earlyStatement, finalStatement);
      }
    }
    return null;
  }

  private boolean isJumpOut(PsiStatement statement) {
    return statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement ||
           (!myHasReturnInLoopOrSwitch && (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement));
  }

  /**
   * @param statement statement to check
   * @return true if given statement could be copied to several return sites. We don't copy {@code throw} or {@code return} 
   * with non-trivial return value as this causes code duplication.
   */
  private boolean isCopyableJumpOut(PsiStatement statement) {
    if (!myHasReturnInLoopOrSwitch && (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement)) {
      return true;
    }
    if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement thenReturn = (PsiReturnStatement)statement;
      return thenReturn.getReturnValue() == null ||
             ExpressionUtils.isSafelyRecomputableExpression(thenReturn.getReturnValue());
    }
    return false;
  }

  @NotNull
  private InlineTransformer getTransformer(PsiStatement earlyStatement, PsiStatement finalStatement) {
    return (methodCopy, callSite, returnType) -> {
      PsiCodeBlock block = Objects.requireNonNull(methodCopy.getBody());
      PsiReturnStatement[] returns = PsiUtil.findReturnStatements(methodCopy);
      for (PsiReturnStatement returnStatement : returns) {
        PsiExpression returnValue = Objects.requireNonNull(returnStatement.getReturnValue()); // null-checked in "from" method
        PsiLiteralExpression literal = ExpressionUtils.getLiteral(returnValue);
        Boolean value = literal == null ? null : ObjectUtils.tryCast(literal.getValue(), Boolean.class);
        if (value == null) {
          CommentTracker tracker = new CommentTracker();
          String condition = myEarlyReturnValue ? tracker.text(returnValue) : BoolUtils.getNegatedExpressionText(returnValue, tracker);
          tracker.replaceAndRestoreComments(returnStatement, "if(" + condition + ") {" + earlyStatement.getText() + "}");
        }
        else if (value == myEarlyReturnValue) {
          new CommentTracker().replaceAndRestoreComments(returnStatement, earlyStatement);
        }
        else {
          new CommentTracker().delete(returnStatement);
        }
      }
      if (finalStatement != null) {
        block.addBefore(finalStatement, block.getRBrace());
      }
      return null;
    };
  }

  @Nullable
  static BooleanReturnModel from(@NotNull PsiCodeBlock body, PsiReturnStatement @NotNull [] returns) {
    List<PsiExpression> terminal = new ArrayList<>();
    boolean earlyValue = false;
    int earlyCount = 0;
    boolean hasReturnInLoopOrSwitch = false;
    for (PsiReturnStatement returnStatement : returns) {
      if (!hasReturnInLoopOrSwitch) {
        hasReturnInLoopOrSwitch = isInLoopOrSwitch(body, returnStatement);
      }
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue == null || !PsiType.BOOLEAN.equals(returnValue.getType())) return null;
      if (ControlFlowUtils.blockCompletesWithStatement(body, returnStatement)) {
        terminal.add(returnValue);
      }
      else {
        PsiLiteralExpression literal = ExpressionUtils.getLiteral(returnValue);
        if (literal == null) return null;
        Boolean literalValue = ObjectUtils.tryCast(literal.getValue(), Boolean.class);
        if (literalValue == null) return null;
        if (earlyCount == 0) {
          earlyValue = literalValue;
        }
        else if (earlyValue != literalValue) {
          return null;
        }
        earlyCount++;
      }
    }
    if (earlyCount == 0) return null;
    int terminalCount = 0;
    for (PsiExpression value : terminal) {
      PsiLiteralExpression literal = ExpressionUtils.getLiteral(value);
      if (literal == null || !(literal.getValue() instanceof Boolean)) {
        terminalCount++;
      }
      else if (literal.getValue().equals(earlyValue)) {
        earlyCount++;
      }
    }
    return new BooleanReturnModel(earlyValue, earlyCount, terminalCount, hasReturnInLoopOrSwitch);
  }

  private static boolean isInLoopOrSwitch(@NotNull PsiCodeBlock body, @NotNull PsiReturnStatement returnStatement) {
    PsiElement parent = returnStatement.getParent();
    while (parent != body) {
      if (parent instanceof PsiLoopStatement || parent instanceof PsiSwitchStatement) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }
}
