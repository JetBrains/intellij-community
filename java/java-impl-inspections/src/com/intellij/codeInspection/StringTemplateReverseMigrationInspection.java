// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class StringTemplateReverseMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTemplateExpression(@NotNull PsiTemplateExpression expression) {
        PsiTemplate template = expression.getTemplate();
        PsiLiteralExpression literal = expression.getLiteralExpression();
        if (template == null && literal == null) return;
        PsiExpression processor = PsiUtil.deparenthesizeExpression(expression.getProcessor());
        if (!(processor instanceof PsiReferenceExpression reference) || !"STR".equals(reference.getReferenceName())) return;
        PsiElement target = reference.resolve();
        if (target != null) {
          if (!(target instanceof PsiField field)) return;
          PsiClass aClass = field.getContainingClass();
          if (aClass == null || !CommonClassNames.JAVA_LANG_STRING_TEMPLATE.equals(aClass.getQualifiedName())) return;
        }
        else if (reference.getQualifierExpression() != null) return;
        if (template != null && ContainerUtil.exists(template.getFragments(), f -> f.getValue() == null)) return;
        if (literal != null && literal.getValue() == null) return;
        holder.registerProblem(expression,
                               JavaBundle.message("inspection.string.template.reverse.migration.string.message"),
                               new ReplaceWithStringConcatenationFix());
      }
    };
  }

  private static class ReplaceWithStringConcatenationFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.string.concatenation.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiTemplateExpression templateExpression)) return;
      PsiLiteralExpression literal = templateExpression.getLiteralExpression();
      if (literal != null) {
        templateExpression.replace(literal);
        return;
      }
      PsiTemplate template = templateExpression.getTemplate();
      if (template == null) return;
      List<@NotNull PsiFragment> fragments = template.getFragments();
      List<@NotNull PsiExpression> expressions = template.getEmbeddedExpressions();
      CommentTracker ct = new CommentTracker();
      StringBuilder concatenation = new StringBuilder();
      boolean start = true;
      for (int i = 0; i < expressions.size(); i++) {
        PsiFragment fragment = fragments.get(i);
        String value = fragment.getValue();
        if (value == null) return;
        if (!value.isEmpty()) {
          if (!concatenation.isEmpty()) concatenation.append('+');
          concatenation.append('"').append(StringUtil.escapeStringCharacters(value)).append('"');
          start = false;
        }
        if (!concatenation.isEmpty()) concatenation.append('+');
        PsiExpression expression = expressions.get(i);
        int precedence = PsiPrecedenceUtil.getPrecedence(expression);
        boolean needParentheses = 
          precedence > PsiPrecedenceUtil.ADDITIVE_PRECEDENCE ||
          !start && precedence == PsiPrecedenceUtil.ADDITIVE_PRECEDENCE && !ExpressionUtils.hasStringType(expression);
        if (needParentheses) {
          concatenation.append('(').append(ct.text(expression)).append(')');
        }
        else {
          String text = ct.text(expression);
          concatenation.append(text.isEmpty() ? "null" : text);
        }
      }
      String last = fragments.get(fragments.size() - 1).getValue();
      if (last == null) return;
      if (!last.isEmpty()) {
        concatenation.append("+\"").append(StringUtil.escapeStringCharacters(last)).append('"');
      }
      ct.replaceAndRestoreComments(templateExpression, concatenation.toString());
    }
  }
}
