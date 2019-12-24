// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
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
import java.util.Arrays;
import java.util.stream.Collectors;

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
          if (nNewLines > 1) continue;
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
        }
        if (nNewLines <= 1) return;
        boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
        holder.registerProblem(expression, quickFixOnly ? null : firstNewLineTextRange,
                               InspectionsBundle.message("inspection.text.block.migration.message", "Concatenation"),
                               new ReplaceWithTextBlockFix());
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
        if (!mySuggestLiteralReplacement && !quickFixOnly) return;
        PsiLiteralExpressionImpl literal = getLiteralExpression(expression);
        if (literal == null) return;
        String text = literal.getText();
        int newLineIdx = getNewLineIndex(text, 0);
        if (newLineIdx == -1 || getNewLineIndex(text, newLineIdx + 1) == -1) return;
        holder.registerProblem(expression, quickFixOnly ? null : new TextRange(newLineIdx, newLineIdx + 2),
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
      String[] lines = getContentLines(operands);
      if (lines == null) return;
      String textBlock = getTextBlock(lines);
      PsiReplacementUtil.replaceExpression(toReplace, textBlock, new CommentTracker());
    }

    @NotNull
    private static String getTextBlock(@NotNull String[] lines) {
      lines = getTextBlockLines(lines);
      int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, true);
      // we need additional indent call only when significant trailing line is missing
      if (indent > 0 && lines.length > 0 && lines[lines.length - 1].endsWith("\n")) indent = 0;
      return "\"\"\"\n" + concatenateTextBlockLines(lines, indent) + "\"\"\"" + (indent > 0 ? ".indent(" + indent + ")" : "");
    }

    @NotNull
    private static String[] getTextBlockLines(@NotNull String[] lines) {
      StringBuilder blockLines = new StringBuilder();
      boolean escapeStartQuote = false;
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        boolean isLastLine = i == lines.length - 1;
        line = PsiLiteralUtil.escapeTextBlockCharacters(line, escapeStartQuote, isLastLine, isLastLine);
        escapeStartQuote = line.endsWith("\"");
        blockLines.append(line);
      }
      return blockLines.toString().split("(?<=\n)");
    }

    private static String concatenateTextBlockLines(@NotNull String[] lines, int indent) {
      if (indent <= 0) return StringUtil.join(lines);
      return Arrays.stream(lines).map(line -> indent < line.length() ? line.substring(indent) : line).collect(Collectors.joining());
    }

    @Nullable
    private static String[] getContentLines(@NotNull PsiExpression[] operands) {
      String[] lines = new String[operands.length];
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = operands[i];
        PsiLiteralExpressionImpl literal = getLiteralExpression(operand);
        if (literal == null) return null;
        String line = getLiteralText(literal);
        if (line == null) return null;
        lines[i] = line;
      }
      return lines;
    }

    @Nullable
    private static String getLiteralText(@NotNull PsiLiteralExpressionImpl literal) {
      if (literal.getLiteralElementType() == JavaTokenType.STRING_LITERAL) return literal.getInnerText();
      Object value = literal.getValue();
      return value == null ? null : value.toString();
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
}
