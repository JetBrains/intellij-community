// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class TextBlockMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean mySuggestLiteralReplacement = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.text.block.migration.suggest.literal.replacement"),
                                          this,
                                          "mySuggestLiteralReplacement");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        if (!isConcatenation(expression)) return;
        int nNewLines = 0;
        PsiExpression[] operands = expression.getOperands();
        TextRange firstNewLineTextRange = null;
        for (PsiExpression operand : operands) {
          PsiLiteralExpressionImpl literal = getLiteralExpression(operand);
          if (literal == null) return;
          String text = literal.getText();
          int newLineIdx = getNewLineIndex(text, 0);
          if (newLineIdx == -1) continue;
          if (firstNewLineTextRange == null) {
            int operandOffset = literal.getTextOffset() - expression.getTextOffset();
            firstNewLineTextRange = new TextRange(operandOffset + newLineIdx, operandOffset + newLineIdx + 2);
          }
          while (nNewLines <= 1 && newLineIdx != -1) {
            nNewLines++;
            newLineIdx = getNewLineIndex(text, newLineIdx + 1);
          }
          if (nNewLines > 1) break;
        }
        if (nNewLines <= 1) return;
        holder.registerProblem(expression, firstNewLineTextRange,
                               InspectionsBundle.message("inspection.text.block.migration.message", "Concatenation"),
                               new ReplaceWithTextBlockFix());
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        if (!mySuggestLiteralReplacement) return;
        PsiLiteralExpressionImpl literal = getLiteralExpression(expression);
        if (literal == null) return;
        String text = literal.getText();
        int newLineIdx = getNewLineIndex(text, 0);
        if (newLineIdx == -1 || getNewLineIndex(text, newLineIdx + 1) == -1) return;
        holder.registerProblem(expression, new TextRange(newLineIdx, newLineIdx + 2),
                               InspectionsBundle.message("inspection.text.block.migration.message", "String"),
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
      PsiLiteralExpressionImpl literalExpression = tryCast(expression, PsiLiteralExpressionImpl.class);
      if (literalExpression != null && literalExpression.getLiteralElementType() == JavaTokenType.STRING_LITERAL) {
        replaceWithTextBlock(new PsiLiteralExpressionImpl[]{literalExpression}, literalExpression);
        return;
      }
      PsiPolyadicExpression polyadicExpression = tryCast(expression, PsiPolyadicExpression.class);
      if (polyadicExpression == null || !isConcatenation(polyadicExpression)) return;
      replaceWithTextBlock(polyadicExpression.getOperands(), polyadicExpression);
    }

    private static void replaceWithTextBlock(@NotNull PsiExpression[] operands, @NotNull PsiExpression toReplace) {
      StringBuilder textBlock = new StringBuilder();
      textBlock.append("\"\"\"\n");
      boolean escapeStartQuote = false;
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = operands[i];
        PsiLiteralExpressionImpl literal = getLiteralExpression(operand);
        if (literal == null) return;
        String text = getLiteralText(literal);
        if (text == null) return;
        boolean isLastLine = i == operands.length - 1;
        text = PsiLiteralUtil.escapeTextBlockCharacters(text, escapeStartQuote, isLastLine, isLastLine);
        escapeStartQuote = text.endsWith("\"");
        textBlock.append(text);
      }
      textBlock.append("\"\"\"");
      PsiReplacementUtil.replaceExpression(toReplace, textBlock.toString(), new CommentTracker());
    }
  }

  private static int getNewLineIndex(@NotNull String text, int start) {
    int i = start;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (i + 1 < text.length() && text.charAt(i + 1) == 'n') return i;
        i += 2;
      }
      else {
        i++;
      }
    }
    return -1;
  }

  private static boolean isConcatenation(@NotNull PsiPolyadicExpression expression) {
    PsiType type = expression.getType();
    return type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  @Nullable
  private static PsiLiteralExpressionImpl getLiteralExpression(@NotNull PsiExpression expression) {
    PsiLiteralExpressionImpl literal = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiLiteralExpressionImpl.class);
    if (literal == null || literal.getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL) return null;
    return literal;
  }

  @Nullable
  private static String getLiteralText(@NotNull PsiLiteralExpressionImpl literal) {
    if (literal.getLiteralElementType() == JavaTokenType.STRING_LITERAL) return literal.getInnerText();
    Object value = literal.getValue();
    return value == null ? null : value.toString();
  }
}
