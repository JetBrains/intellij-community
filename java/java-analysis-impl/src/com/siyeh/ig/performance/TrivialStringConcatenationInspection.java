/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public final class TrivialStringConcatenationInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public boolean skipIfNecessary = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("skipIfNecessary",
                       InspectionGadgetsBundle.message("trivial.string.concatenation.option.only.necessary"))
    );
  }

  @Override
  @NotNull
  public String getID() {
    return "ConcatenationWithEmptyString";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("trivial.string.concatenation.problem.descriptor");
  }

  private static void fixBinaryExpression(PsiPolyadicExpression expression) {
    CommentTracker commentTracker = new CommentTracker();
    PsiExpression[] operands = expression.getOperands();
    final PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(operands[0]);
    final PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(operands[1]);
    final PsiExpression replacement = ExpressionUtils.isEmptyStringLiteral(lOperand) ? rOperand : lOperand;
    String newText = replacement == null ? "" : buildReplacement(replacement, false, commentTracker);
    PsiReplacementUtil.replaceExpression(expression, newText, commentTracker);
  }

  private static void fixLastExpression(PsiPolyadicExpression polyadicExpression, boolean seenStringBefore) {
    PsiExpression[] operands = polyadicExpression.getOperands();
    PsiExpression beforeLast = operands[operands.length - 2];
    StringBuilder builder = new StringBuilder();
    boolean meetBeforeLast = false;
    boolean afterPlus = false;
    CommentTracker generalCommentTracker = new CommentTracker();
    CommentTracker beforeLastCommentTracker = new CommentTracker();
    for (PsiElement child : polyadicExpression.getChildren()) {
      if (!meetBeforeLast) {
        if (beforeLast == child) {
          meetBeforeLast = true;
        }
        builder.append(child.getText());
        generalCommentTracker.markUnchanged(child);
      }
      else {
        if (child instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.PLUS) {
          afterPlus = true;
        }
        if (!afterPlus) {
          beforeLastCommentTracker.grabComments(child);
          generalCommentTracker.markUnchanged(child);
        }
      }
    }

    String text = builder.toString().trim();
    if (!seenStringBefore) {
      text = "String.valueOf(" + text + ')';
    }

    String finalText = text;
    final PsiElement replacementExpression =
      CodeStyleManager.getInstance(polyadicExpression.getProject()).performActionWithFormatterDisabled(new Computable<>() {
        @Override
        public PsiElement compute() {
          return generalCommentTracker.replaceAndRestoreComments(polyadicExpression, finalText);
        }
      });

    if (replacementExpression instanceof PsiPolyadicExpression psiPolyadicExpression) {
      PsiExpression[] expressionOperands = psiPolyadicExpression.getOperands();
      if (expressionOperands.length == 0) {
        return;
      }
      PsiExpression lastOperand = expressionOperands[expressionOperands.length - 1];
      beforeLastCommentTracker.insertCommentsBefore(lastOperand);
    }
  }

  private static void fixExpressionInMiddle(PsiExpression expression, PsiPolyadicExpression polyadicExpression, boolean seenString) {
    PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (parent == null) {
      return;
    }

    //might be null if expression is the first operand
    @Nullable PsiElement previousPlus = expression.getPrevSibling();
    while (previousPlus != null && !(previousPlus instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.PLUS)) {
      previousPlus = previousPlus.getPrevSibling();
    }

    PsiElement nextPlus = expression.getNextSibling();
    while (nextPlus != null && !(nextPlus instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.PLUS)) {
      nextPlus = nextPlus.getNextSibling();
    }
    if (nextPlus == null) {
      return;
    }

    int position = 0;
    PsiExpression[] operands = polyadicExpression.getOperands();
    for (int i = 0; i < operands.length; i++) {
      if (operands[i] == expression) {
        position = i;
      }
    }

    if (position >= operands.length - 1) {
      return;
    }

    PsiExpression nextExpression = operands[position + 1];
    StringBuilder builder = new StringBuilder();

    boolean isFirstOperand = operands[0] == expression;
    boolean meetPreviousPlus = isFirstOperand;
    boolean meetNextPlus = false;
    boolean meetNextOperand = false;
    CommentTracker generalTracker = new CommentTracker();
    CommentTracker firstTracker = new CommentTracker();
    //base case: ..expr_+_""_+_expr..
    for (PsiElement child : polyadicExpression.getChildren()) {
      if (!meetPreviousPlus) {
        // .<here>.expr_+_""_+_expr..
        if (previousPlus != child) {
          builder.append(child.getText());
          generalTracker.markUnchanged(child);
        }
        else {
          // ..expr_+<here>_""_+_expr..
          meetPreviousPlus = true;
          builder.append(child.getText());
        }
      }
      else if (!meetNextPlus) {
        //skip elements between pluses
        if (nextPlus == child) {
          // ..expr_+_""_+<here>_expr..
          meetNextPlus = true;
        }
      }
      else if (!meetNextOperand) {
        if (nextExpression != child) {
          if (isFirstOperand) {
            // ""_+_<here>expr - it is impossible to build expression with comments in the beginning
            firstTracker.grabComments(child);
            generalTracker.markUnchanged(child);
          }
          else {
            // ..expr_+_""_+_<here>expr..
            builder.append(child.getText());
            generalTracker.markUnchanged(child);
          }
        }
        else {
          // ..expr_+_""_+_expr<here>..
          builder.append(buildReplacement(nextExpression, seenString, generalTracker));
          meetNextOperand = true;
        }
      }
      else {
        // ..expr_+_""_+_expr<here>..<here>
        builder.append(child.getText());
        generalTracker.markUnchanged(child);
      }
    }
    final PsiElement replacementExpression =
      CodeStyleManager.getInstance(polyadicExpression.getProject()).performActionWithFormatterDisabled(new Computable<>() {
        @Override
        public PsiElement compute() {
          return generalTracker.replaceAndRestoreComments(polyadicExpression, builder.toString().trim());
        }
      });
    if (replacementExpression instanceof PsiPolyadicExpression psiPolyadicExpression) {
      PsiExpression[] expressionOperands = psiPolyadicExpression.getOperands();
      if (expressionOperands.length - 1 < position) {
        return;
      }
      firstTracker.insertCommentsBefore(replacementExpression);
    }
  }

  @NonNls
  static String buildReplacement(@NotNull PsiExpression operandToReplace,
                                 boolean seenString,
                                 CommentTracker commentTracker) {
    if (ExpressionUtils.isNullLiteral(operandToReplace)) {
      if (seenString) {
        return "null";
      }
      else {
        return "String.valueOf((Object) null)";
      }
    }
    if (seenString || ExpressionUtils.hasStringType(operandToReplace)) {
      return operandToReplace.getText();
    }
    PsiExpression skipDown = PsiUtil.skipParenthesizedExprDown(operandToReplace);
    operandToReplace = skipDown == null ? operandToReplace : skipDown;
    return "String.valueOf(" + commentTracker.text(operandToReplace) + ')';
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryTemporaryObjectFix();
  }

  private static class UnnecessaryTemporaryObjectFix extends PsiUpdateModCommandQuickFix {

    private final @IntentionName String m_name;

    UnnecessaryTemporaryObjectFix() {
      m_name = InspectionGadgetsBundle.message("string.replace.quickfix");
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.temporary.object.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiElement current = startElement;
      while (current.getParent() instanceof PsiParenthesizedExpression) {
        current = current.getParent();
      }
      if (!(current instanceof PsiExpression expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiPolyadicExpression polyadicExpression)) {
        return;
      }
      PsiType polyadicExpressionType = polyadicExpression.getType();
      if (polyadicExpressionType == null || !polyadicExpressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }

      PsiExpression[] operands = polyadicExpression.getOperands();

      //for fix all case, but I think, it is impossible
      if (operands.length == 1) {
        return;
      }

      if (operands.length == 2) {
        fixBinaryExpression(polyadicExpression);
        return;
      }

      boolean seenString = Stream.of(operands)
        .takeWhile(t -> t != expression)
        .anyMatch(t -> t.getType() != null && t.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING));

      if (operands[0] == expression) {
        PsiType type2 = operands[2].getType();
        if (type2 != null && type2.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          seenString = true;
        }
      }

      if (operands[operands.length - 1] == expression) {
        fixLastExpression(polyadicExpression, seenString);
      }
      else {
        fixExpressionInMiddle(expression, polyadicExpression, seenString);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TrivialStringConcatenationVisitor();
  }

  private class TrivialStringConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      final boolean constant = PsiUtil.isConstantExpression(expression);
      boolean seenString = false;
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = operands[i];
        operand = PsiUtil.skipParenthesizedExprDown(operand);
        if (operand == null) {
          return;
        }
        if (i > 0 && !seenString) {
          PsiExpression previous = operands[i - 1];
          if (ExpressionUtils.hasStringType(previous)) {
            seenString = true;
          }
        }
        if (!ExpressionUtils.isEmptyStringLiteral(operand)) {
          continue;
        }
        if ((skipIfNecessary || constant) && !seenString) {
          if (i == operands.length - 1) {
            //example (where x is int):
            // ... + x + "";
            continue;
          }

          PsiExpression next = operands[i + 1];
          if (!ExpressionUtils.hasStringType(next)) {
            if (operands.length == 2) {
              //example (where x is int):
              // "" + x;
              continue;
            }

            if (i == 0) {
              PsiExpression nextNext = operands[i + 2];
              if (!ExpressionUtils.hasStringType(nextNext) || ExpressionUtils.isEmptyStringLiteral(nextNext)) {
                //example (where x is int):
                // "" + x + 1 + ...;
                continue;
              }
            }
            else {
              //example (where x is int):
              // ... + x + "" + x + ...;
              continue;
            }
          }
        }
        registerError(operand, operand);
      }
    }
  }
}
