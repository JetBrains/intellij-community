// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public final class SplitConditionUtil {
  public static PsiPolyadicExpression findCondition(PsiElement element) {
    return findCondition(element, true, true);
  }

  public static PsiPolyadicExpression findCondition(PsiElement element, boolean acceptAnd, boolean acceptOr) {
    if (!(element instanceof PsiJavaToken)) {
      return null;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    if (!(token.getParent() instanceof PsiPolyadicExpression)) return null;

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    boolean isAndExpression = acceptAnd && expression.getOperationTokenType() == JavaTokenType.ANDAND;
    boolean isOrExpression = acceptOr && expression.getOperationTokenType() == JavaTokenType.OROR;
    if (!isAndExpression && !isOrExpression) return null;
    if (ErrorUtil.containsError(expression)) {
      // Incomplete expression like "something &&"
      return null;
    }

    while (expression.getParent() instanceof PsiPolyadicExpression) {
      expression = (PsiPolyadicExpression)expression.getParent();
      if (isAndExpression && expression.getOperationTokenType() != JavaTokenType.ANDAND) return null;
      if (isOrExpression && expression.getOperationTokenType() != JavaTokenType.OROR) return null;
    }
    return expression;
  }

  public static PsiExpression getROperands(PsiPolyadicExpression expression, PsiJavaToken separator) {
    return getROperands(expression, separator, new CommentTracker());
  }

  public static PsiExpression getROperands(PsiPolyadicExpression expression, PsiJavaToken separator, CommentTracker ct) {
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(separator);
    final int offsetInParent;
    if (next == null) {
      offsetInParent = separator.getStartOffsetInParent() + separator.getTextLength();
    } else {
      ct.markRangeUnchanged(next, expression.getLastChild());
      offsetInParent = next.getStartOffsetInParent();
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    String rOperands = expression.getText().substring(offsetInParent);
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  public static PsiExpression getLOperands(@NotNull PsiPolyadicExpression expression, @NotNull PsiJavaToken separator) {
    return getLOperands(expression, separator, new CommentTracker());
  }

  public static PsiExpression getLOperands(@NotNull PsiPolyadicExpression expression,
                                           @NotNull PsiJavaToken separator,
                                           @NotNull CommentTracker ct) {
    PsiElement prev = separator;
    if (prev.getPrevSibling() instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
    ct.markRangeUnchanged(expression.getFirstChild(), prev.getPrevSibling());

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    String rOperands = expression.getText().substring(0, prev.getStartOffsetInParent());
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  @Nullable
  static PsiIfStatement create(@NotNull PsiElementFactory factory,
                               @NotNull PsiIfStatement ifStatement,
                               @NotNull PsiExpression extract,
                               @NotNull PsiExpression leave,
                               @NotNull IElementType operation,
                               CommentTracker tracker) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return null;
    }
    PsiStatement elseBranch = ifStatement.getElseBranch();

    if (operation == JavaTokenType.OROR) {
      return createOrOr(factory, thenBranch, elseBranch, extract, leave, tracker);
    }
    if (operation == JavaTokenType.ANDAND) {
      return createAndAnd(factory, thenBranch, elseBranch, extract, leave, tracker);
    }

    return null;
  }

  @NotNull
  private static PsiIfStatement createAndAnd(@NotNull PsiElementFactory factory,
                                             @NotNull PsiStatement thenBranch,
                                             @Nullable PsiStatement elseBranch,
                                             @NotNull PsiExpression extract,
                                             @NotNull PsiExpression leave,
                                             CommentTracker tracker) {
    List<String> elseChain = new ArrayList<>();
    boolean chainFinished = false;
    loop:
    while (!chainFinished) {
      PsiIfStatement nextIf = tryCast(ControlFlowUtils.stripBraces(elseBranch), PsiIfStatement.class);
      if (nextIf == null) break;
      PsiExpression nextCondition = PsiUtil.skipParenthesizedExprDown(nextIf.getCondition());
      if (nextCondition == null) break;
      EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
      if (nextIf.getThenBranch() == null) break;
      if (equivalence.expressionsAreEquivalent(extract, nextCondition)) {
        elseChain.add(tracker.text(nextIf.getThenBranch()));
        chainFinished = true;
      }
      else if (nextIf.getElseBranch() == null && equivalence.expressionsAreEquivalent(
        extract, factory.createExpressionFromText(BoolUtils.getNegatedExpressionText(nextCondition), nextCondition))) {
        // skip duplicating else branch in cases like
        // if(foo && bar) {1} else if (!foo) {2} =>
        // if(foo) { if(bar) {1} } else {2}
        elseBranch = nextIf.getThenBranch();
        chainFinished = true;
        break;
      }
      else {
        if (!(nextCondition instanceof PsiPolyadicExpression)) break;
        PsiPolyadicExpression nextPolyadic = (PsiPolyadicExpression)nextCondition;
        if (!nextPolyadic.getOperationTokenType().equals(JavaTokenType.ANDAND)) break;
        PsiExpression[] nextOperands = nextPolyadic.getOperands();
        PsiExpression[] operands;
        if (extract instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)extract).getOperationTokenType().equals(JavaTokenType.ANDAND)) {
          operands = ((PsiPolyadicExpression)extract).getOperands();
        }
        else {
          operands = new PsiExpression[]{extract};
        }
        if (nextOperands.length <= operands.length) break;
        for (int i = 0; i < operands.length; i++) {
          if (!PsiEquivalenceUtil.areElementsEquivalent(nextOperands[i], operands[i])) break loop;
        }
        PsiExpression nextExtracted =
          getROperands(nextPolyadic, nextPolyadic.getTokenBeforeOperand(nextOperands[operands.length]), tracker);
        elseChain.add(createIfString(nextExtracted, nextIf.getThenBranch(), (PsiStatement)null, tracker));
      }
      elseBranch = nextIf.getElseBranch();
    }
    if (!chainFinished && elseBranch != null) {
      elseChain.add(elseBranch.getText());
    }
    String thenString;
    if (elseChain.isEmpty()) {
      thenString = createIfString(leave, thenBranch, (String)null, tracker);
      if (elseBranch != null) {
        thenString = "{" + thenString + "}";
      }
    }
    else {
      thenString = "{" + createIfString(leave, thenBranch, String.join("\nelse ", elseChain), tracker) + "\n}";
    }
    String ifString = createIfString(extract, thenString, elseBranch, tracker);
    return (PsiIfStatement)factory.createStatementFromText(ifString, thenBranch);
  }

  @NotNull
  private static PsiIfStatement createOrOr(@NotNull PsiElementFactory factory,
                                           @NotNull PsiStatement thenBranch,
                                           @Nullable PsiStatement elseBranch,
                                           @NotNull PsiExpression extract,
                                           @NotNull PsiExpression leave,
                                           CommentTracker tracker) {
    return (PsiIfStatement)factory.createStatementFromText(
      createIfString(extract, thenBranch, createIfString(leave, thenBranch, elseBranch, tracker), tracker), thenBranch);
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull PsiStatement thenBranch,
                                       @Nullable PsiStatement elseBranch,
                                       CommentTracker tracker) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(condition);
    return createIfString(tracker.text(stripped == null ? condition : stripped),
                          toThenBranchString(tracker.markUnchanged(thenBranch)),
                          toElseBranchString(tracker.markUnchanged(elseBranch), false));
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull PsiStatement thenBranch,
                                       @Nullable String elseBranch,
                                       CommentTracker tracker) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(condition);
    return createIfString(tracker.text(stripped == null ? condition : stripped),
                          toThenBranchString(tracker.markUnchanged(thenBranch)), elseBranch);
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull String thenBranch,
                                       @Nullable PsiStatement elseBranch,
                                       CommentTracker tracker) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(condition);
    return createIfString(tracker.text(stripped == null ? condition : stripped),
                          thenBranch, toElseBranchString(tracker.markUnchanged(elseBranch), true));
  }

  @NotNull
  private static String createIfString(@NotNull String condition,
                                       @NotNull String thenBranch,
                                       @Nullable String elseBranch) {
    final String elsePart = elseBranch != null ? "\n else " + elseBranch : "";
    return "if (" + condition + ")\n" + thenBranch + elsePart;
  }

  @NotNull
  private static String toThenBranchString(@NotNull PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement)) {
      return "{ " + statement.getText() + "\n }";
    }

    return statement.getText();
  }

  @Nullable
  private static String toElseBranchString(@Nullable PsiStatement statement, boolean skipElse) {
    if (statement == null) {
      return null;
    }

    if (statement instanceof PsiBlockStatement || skipElse && statement instanceof PsiIfStatement) {
      return statement.getText();
    }

    return "{ " + statement.getText() + "\n }";
  }
}
