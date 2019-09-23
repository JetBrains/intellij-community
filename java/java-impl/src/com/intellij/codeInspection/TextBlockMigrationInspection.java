// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.google.common.base.Strings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class TextBlockMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        if (!isConcatenation(expression)) return;
        int newLinesCnt = 0;
        for (PsiExpression operand : expression.getOperands()) {
          String text = getExpressionText(operand, false);
          if (text == null) return;
          if (newLinesCnt <= 1) newLinesCnt += StringUtils.countMatches(text, "\n");
        }
        if (newLinesCnt <= 1) return;
        holder.registerProblem(expression, InspectionsBundle.message("inspection.text.block.migration.message", "Concatenation"),
                               new ReplaceWithTextBlockFix());
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        String text = getExpressionText(expression, false);
        if (text == null || StringUtils.countMatches(text, "\n") <= 1) return;
        holder.registerProblem(expression, InspectionsBundle.message("inspection.text.block.migration.message", "String"),
                               new ReplaceWithTextBlockFix());
      }
    };
  }

  private static class ReplaceWithTextBlockFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.replace.with.text.block.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(tryCast(descriptor.getPsiElement(), PsiExpression.class));
      if (expression == null) return;
      Document document = PsiDocumentManager.getInstance(project).getDocument(expression.getContainingFile());
      if (document == null) return;
      int expressionOffset = expression.getTextOffset();
      int offset = expressionOffset - document.getLineStartOffset(document.getLineNumber(expressionOffset));
      PsiLiteralExpressionImpl literalExpression = tryCast(expression, PsiLiteralExpressionImpl.class);
      if (literalExpression != null && literalExpression.getLiteralElementType() == JavaTokenType.STRING_LITERAL) {
        replaceWithTextBlock(new PsiLiteralExpressionImpl[]{literalExpression}, offset, literalExpression);
        return;
      }
      PsiPolyadicExpression polyadicExpression = tryCast(expression, PsiPolyadicExpression.class);
      if (polyadicExpression == null || !isConcatenation(polyadicExpression)) return;
      replaceWithTextBlock(polyadicExpression.getOperands(), offset, polyadicExpression);
    }

    private static void replaceWithTextBlock(@NotNull PsiExpression[] operands, int offset, @NotNull PsiExpression toReplace) {
      StringBuilder textBlock = new StringBuilder();
      String indent = Strings.repeat(" ", offset);
      textBlock.append("\"\"\"\n").append(indent);
      boolean escapeStartQuote = false;
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = operands[i];
        String text = getExpressionText(operand, true);
        if (text == null) return;
        boolean isLastLine = i == operands.length - 1;
        text = PsiLiteralUtil.escapeTextBlockCharacters(text, escapeStartQuote, isLastLine, isLastLine);
        escapeStartQuote = text.endsWith("\"");
        textBlock.append(text.replaceAll("\n", '\n' + indent));
      }
      textBlock.append("\"\"\"");
      PsiReplacementUtil.replaceExpression(toReplace, textBlock.toString(), new CommentTracker());
    }
  }

  private static boolean isConcatenation(@NotNull PsiPolyadicExpression expression) {
    PsiType type = expression.getType();
    return type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  @Nullable
  private static String getExpressionText(@NotNull PsiExpression expression, boolean isRawText) {
    PsiLiteralExpressionImpl literal = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiLiteralExpressionImpl.class);
    if (literal == null || literal.getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL) return null;
    if (literal.getLiteralElementType() == JavaTokenType.STRING_LITERAL && isRawText) return literal.getInnerText();
    Object value = literal.getValue();
    return value == null ? null : value.toString();
  }
}
