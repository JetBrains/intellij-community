// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.JavaPsiStringTemplateUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class RedundantEmbeddedExpressionInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.STRING_TEMPLATES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitTemplateExpression(@NotNull PsiTemplateExpression expression) {
        PsiTemplate template = expression.getTemplate();
        if (template == null) return;
        if (!JavaPsiStringTemplateUtil.isStrTemplate(expression.getProcessor())) return;
        for (PsiExpression embeddedExpression : template.getEmbeddedExpressions()) {
          if (isEmbeddedLiteralRedundant(embeddedExpression)) {
            holder.problem(embeddedExpression, InspectionGadgetsBundle.message("inspection.redundant.embedded.expression.message.literal"))
              .fix(new RemoveEmbeddedExpressionFix(embeddedExpression)).register();
          }
          else if (embeddedExpression instanceof PsiEmptyExpressionImpl) {
            String text = template.getText();
            TextRange rangeInParent = embeddedExpression.getTextRangeInParent();
            int startIndex = text.lastIndexOf("\\{", rangeInParent.getStartOffset());
            int endIndex = text.indexOf("}", rangeInParent.getEndOffset());
            if (startIndex != -1 && endIndex != -1) {
              holder.problem(template, InspectionGadgetsBundle.message("inspection.redundant.embedded.expression.message.empty"))
                .range(TextRange.create(startIndex, endIndex + 1))
                .fix(new RemoveEmbeddedExpressionFix(embeddedExpression))
                .register();
            }
          } 
        }
      }
    };
  }

  /**
   * Determines if an embedded literal expression is redundant and can be inlined.
   *
   * @param embeddedExpression the embedded expression to check
   * @return true if the embedded literal expression is redundant, false otherwise
   */
  public static boolean isEmbeddedLiteralRedundant(@Nullable PsiExpression embeddedExpression) {
    if (embeddedExpression instanceof PsiLiteralExpression literal && !literal.isTextBlock()) {
      Object value = literal.getValue();
      return value instanceof String || String.valueOf(literal.getValue()).equals(literal.getText());
    }
    return false;
  }

  /**
   * Inlines the given embedded expression within a template expression.
   *
   * @param expression the embedded expression to be inlined
   * @return updated template expression; null if inlining fails (e.g., there's no parent template) 
   */
  public static @Nullable PsiTemplateExpression inlineEmbeddedExpression(@NotNull PsiExpression expression) {
    PsiTemplate template = (PsiTemplate)expression.getParent();
    if (template == null) return null;
    PsiTemplateExpression templateExpression = ObjectUtils.tryCast(template.getParent(), PsiTemplateExpression.class);
    if (templateExpression == null) return null;
    PsiElement[] children = template.getChildren();
    int index = ArrayUtil.indexOf(children, expression);
    if (index == -1) return null;
    StringBuilder newTemplate = new StringBuilder();
    for (PsiElement child : templateExpression.getChildren()) {
      if (child == template) break;
      newTemplate.append(child.getText());
    }
    boolean suffixProcessed = false;
    boolean prefixProcessed = false;
    CommentTracker ct = new CommentTracker();
    PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(expression);
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(expression);
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      String childText = child.getText();
      if (i < index && prefixProcessed) continue;
      if (prev == child) {
        childText = StringUtil.trimEnd(childText, "\\{");
        prefixProcessed = true;
      } else if (next == child) {
        childText = StringUtil.trimStart(childText, "}");
        suffixProcessed = true;
      }
      if (i > index && !suffixProcessed) continue;
      if (child == expression) {
        if (expression instanceof PsiEmptyExpressionImpl) {
          childText = "null";
        } else if (expression instanceof PsiLiteralExpression literal) {
          Object value = literal.getValue();
          if (value instanceof String && !literal.isTextBlock()) {
            childText = StringUtil.trimEnd(StringUtil.trimStart(childText, "\""), "\"");
          } else {
            childText = StringUtil.escapeStringCharacters(String.valueOf(value));
          }
        }
      }
      ct.markUnchanged(child);
      newTemplate.append(childText);
    }
    PsiTemplateExpression newTemplateExpression = (PsiTemplateExpression)JavaPsiFacade.getElementFactory(expression.getProject())
      .createExpressionFromText(newTemplate.toString(), expression);
    return (PsiTemplateExpression)ct.replaceAndRestoreComments(templateExpression, newTemplateExpression);
  }

  private static class RemoveEmbeddedExpressionFix extends PsiUpdateModCommandAction<PsiExpression> {
    private RemoveEmbeddedExpressionFix(PsiExpression embeddedExpression) {
      super(embeddedExpression);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
      inlineEmbeddedExpression(expression);
    }
    
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.embedded.expression.fix.family.name");
    }
  }
}
