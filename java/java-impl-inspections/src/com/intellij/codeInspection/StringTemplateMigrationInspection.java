// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.redundancy.RedundantEmbeddedExpressionInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
        final ProblemHighlightType type = getProblemHighlightType(expression);
        if (type == null || (type == ProblemHighlightType.INFORMATION && !isOnTheFly)) return;
        holder.registerProblem(expression,
                               JavaBundle.message("inspection.string.template.migration.string.message"),
                               type, new ReplaceWithStringTemplateFix());
      }

      @Nullable
      private static ProblemHighlightType getProblemHighlightType(@NotNull PsiPolyadicExpression expression) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiExpression.class);
        if (parent instanceof PsiNameValuePair || parent instanceof PsiCaseLabelElementList || parent instanceof PsiAnnotationMethod) {
          return null;
        }

        boolean hasString = false;
        boolean hasNotLiteralExpression = false;
        boolean hasLiteralExpression = false;

        for (PsiExpression operand : expression.getOperands()) {
          // Support for template concatenation is not yet implemented.
          if (operand instanceof PsiTemplateExpression) {
            return null;
          }
          if (operand instanceof PsiLiteralExpression && ExpressionUtils.hasStringType(operand)) {
            hasString = true;
          }
          // (1 + 2) * 3 + "str"
          else if (!isOnlyLiterals(operand)) {
            hasNotLiteralExpression = true;
          }
          else {
            hasLiteralExpression = true;
          }
        }
        // "str" + str
        if (hasString && hasNotLiteralExpression) {
          return ProblemHighlightType.WEAK_WARNING;
        }

        // (str + str) || "str" + 1 + 2)
        if (hasNotLiteralExpression || (hasString && hasLiteralExpression) || PsiUtil.isConstantExpression(expression)) {
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
        else if (operand instanceof PsiParenthesizedExpression) {
          final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(operand);
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
      String stringTemplate = buildReplacementStringTemplate(polyadicExpression);
      if (stringTemplate == null) return;
      CommentTracker tracker = new CommentTracker();
      PsiElement result = tracker.replaceAndRestoreComments(polyadicExpression, stringTemplate);
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      if (result instanceof PsiTemplateExpression template) {
        replaceRedundantEmbeddedExpression(template);
      }
    }

    private static String buildReplacementStringTemplate(@NotNull PsiPolyadicExpression expression) {
      StringBuilder content = new StringBuilder();
      boolean isStringFound = false;
      boolean textBlock = useTextBlockTemplate(expression);
      for (PsiExpression operand : expression.getOperands()) {
        if (!isStringFound && ExpressionUtils.hasStringType(operand)) {
          isStringFound = true;
          if (!content.isEmpty()) {
            content.insert(0, "\\{").append("}");
          }
        }

        if (operand instanceof PsiParenthesizedExpression parenthesized) {
          operand = PsiUtil.skipParenthesizedExprDown(parenthesized);
        }
        if (operand instanceof PsiLiteralExpression literal) {
          if (ExpressionUtils.hasStringType(literal)) {
            String value = (String)literal.getValue();
            if (value == null) return null; // error in string literal
            String escaped = StringUtil.escapeStringCharacters(value);
            content.append(textBlock ? PsiLiteralUtil.escapeTextBlockCharacters(escaped, false, true, false) : escaped);
          }
          else {
            toTemplateExpression(content, isStringFound, literal);
          }
        }
        else {
          toTemplateExpression(content, isStringFound, operand);
        }
      }
      return textBlock ? CommonClassNames.JAVA_LANG_STRING_TEMPLATE + ".STR.\"\"\"\n" + content + "\"\"\"" : CommonClassNames.JAVA_LANG_STRING_TEMPLATE + ".STR.\"" + content + "\"";
    }

    private static boolean useTextBlockTemplate(PsiPolyadicExpression expression) {
      for (PsiExpression operand : expression.getOperands()) {
        if (operand instanceof PsiLiteralExpression literal && ExpressionUtils.hasStringType(operand)) {
          if (literal.isTextBlock()) {
            return true;
          }
          String value = (String)literal.getValue();
          if (value != null && value.contains("\n")) {
            return true;
          }
        }
      }
      return false;
    }

    private static void replaceRedundantEmbeddedExpression(PsiTemplateExpression template) {
      while (template != null && template.getTemplate() != null) {
        List<@NotNull PsiExpression> expressions = template.getTemplate().getEmbeddedExpressions();
        for (PsiExpression expression : expressions) {
          if (RedundantEmbeddedExpressionInspection.isEmbeddedLiteralRedundant(expression)) {
            template = RedundantEmbeddedExpressionInspection.inlineEmbeddedExpression(expression);
            if (template != null) break; // try again replacement with new embedded expressions
          }
          template = null; // finish
        }
      }
    }

    private static void toTemplateExpression(@NotNull StringBuilder result, boolean isEmbeddedExpression, @NotNull PsiElement element) {
      if (isEmbeddedExpression || result.isEmpty()) {
        if (isEmbeddedExpression) {
          result.append("\\{").append(element.getText()).append("}");
        }
        else {
          result.append(element.getText());
        }
      }
      else {
        result.append("+").append(element.getText());
      }
    }
  }
}
