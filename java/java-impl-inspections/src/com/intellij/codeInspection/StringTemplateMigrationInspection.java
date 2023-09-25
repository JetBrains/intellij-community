// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class StringTemplateMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.STRING_TEMPLATES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
        if (expression.getOperationTokenType() != JavaTokenType.PLUS) return;
        if (!ExpressionUtils.hasStringType(expression)) return;
        final ProblemHighlightType type = getAvailableType(expression.getOperands());
        if (type == null) return;
        holder.registerProblem(expression,
                               JavaBundle.message("inspection.string.template.migration.string.message"),
                               type, new ReplaceWithStringTemplateFix());
      }

      @Nullable
      private static ProblemHighlightType getAvailableType(PsiExpression @NotNull [] operands) {
        var available = new Object() {
          boolean hasString = false;
          boolean hasNotLiteralExpression = false;
          boolean hasLiteralExpression = false;
        };

        for (PsiExpression operand : operands) {
          if (operand instanceof PsiTemplateExpression) { // Support for template concatenation is not yet implemented.
            return null;
          }
          if (operand instanceof PsiLiteralExpression literal && literal.getValue() instanceof String) {
            available.hasString = true;
          }
          else if (!isOnlyLiterals(operand)) { // (1 + 2) * 3 + "str"
            available.hasNotLiteralExpression = true;
          }
          else {
            available.hasLiteralExpression = true;
          }
        }
        if (available.hasString && available.hasNotLiteralExpression) // "str" + str
        {
          return ProblemHighlightType.WEAK_WARNING;
        }

        if (available.hasNotLiteralExpression || // str + str
            (available.hasString && available.hasLiteralExpression) // "str" + 1 + 2
        ) {
          return ProblemHighlightType.INFORMATION;
        }
        else {
          return null;
        }
      }

      private static boolean isOnlyLiterals(@NotNull PsiExpression operand) {
        if (operand instanceof PsiLiteralExpression) {
          return true;
        }
        else if (operand instanceof PsiPolyadicExpression polyadic) {
          for (PsiExpression expression : polyadic.getOperands()) {
            if (!isOnlyLiterals(expression)) return false;
          }
          return true;
        }
        else if (operand instanceof PsiParenthesizedExpression parenthesized) {
          final PsiExpression expression = parenthesized.getExpression();
          if (expression != null && !isOnlyLiterals(expression)) return false;
          return true;
        }
        else {
          return false;
        }
      }
    };
  }

  private static class ReplaceWithStringTemplateFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.string.template.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(tryCast(element, PsiExpression.class));
      if (expression == null) return;
      PsiPolyadicExpression polyadicExpression = tryCast(expression, PsiPolyadicExpression.class);
      if (polyadicExpression == null || !ExpressionUtils.hasStringType(polyadicExpression)) return;
      replaceWithStringTemplate(polyadicExpression, polyadicExpression);
    }

    private static void replaceWithStringTemplate(@NotNull PsiPolyadicExpression expression, @NotNull PsiExpression toReplace) {
      StringBuilder content = new StringBuilder();
      boolean isStringFounded = false;
      for (PsiExpression operand : expression.getOperands()) {
        if (!isStringFounded && TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, operand.getType())) {
          isStringFounded = true;
          toTemplateExpression(content);
        }

        if (operand instanceof PsiParenthesizedExpression parenthesized) {
          operand = removeParenthesize(parenthesized);
        }
        if (operand instanceof PsiLiteralExpression literal) {
          if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, literal.getType())) {
            content.append(getLiteralText(literal));
          }
          else {
            toTemplateExpression(content, isStringFounded, literal);
          }
        }
        else {
          toTemplateExpression(content, isStringFounded, operand);
        }
      }
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(toReplace, content.insert(0, "STR.\"").append("\"").toString());
    }

    private static void toTemplateExpression(@NotNull StringBuilder result, boolean isParenthesize, @Nullable PsiElement element) {
      if (element != null) {
        if (isParenthesize || result.isEmpty()) {
          toTemplateExpression(result, isParenthesize, element.getText());
        }
        else {
          toTemplateExpression(result, isParenthesize, "+", element.getText());
        }
      }
    }

    private static void toTemplateExpression(@NotNull StringBuilder result, boolean isParenthesize, String... expressions) {
      if (isParenthesize) {
        result.append("\\{");
        for (String expr : expressions) result.append(expr);
        result.append("}");
      }
      else {
        for (String expr : expressions) result.append(expr);
      }
    }

    private static void toTemplateExpression(@NotNull StringBuilder result) {
      if (!result.isEmpty()) {
        result.insert(0, "\\{").append("}");
      }
    }


    @NotNull
    private static String getLiteralText(@NotNull PsiLiteralExpression literal) {
      Object value;
      if (!literal.isTextBlock() && ExpressionUtils.hasStringType(literal)) {
        value = PsiLiteralUtil.getStringLiteralContent(literal);
      }
      else {
        value = literal.getValue();
      }
      return value == null ? "" : value.toString();
    }

    @Nullable
    private static PsiExpression removeParenthesize(@NotNull PsiParenthesizedExpression parenthesized) {
      while (parenthesized.getExpression() != null && parenthesized.getExpression() instanceof PsiParenthesizedExpression) {
        parenthesized = (PsiParenthesizedExpression)parenthesized.getExpression();
      }
      return parenthesized.getExpression();
    }
  }
}
