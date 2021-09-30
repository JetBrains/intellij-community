// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
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
    return new SingleCheckboxOptionsPanel(JavaBundle.message("inspection.text.block.migration.suggest.literal.replacement"),
                                          this,
                                          "mySuggestLiteralReplacement");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        if (!isConcatenation(expression)) return;
        int nNewLines = 0;
        PsiExpression[] operands = expression.getOperands();
        TextRange firstNewLineTextRange = null;
        boolean hasEscapedQuotes = false;
        for (PsiExpression operand : operands) {
          PsiLiteralExpression literal = getLiteralExpression(operand);
          if (literal == null) return;
          if (nNewLines > 1) continue;
          String text = literal.getText();
          hasEscapedQuotes |= (getQuoteIndex(text) != -1);
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
        if (nNewLines > 1) {
          boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
          holder.registerProblem(expression, quickFixOnly ? null : firstNewLineTextRange,
                                 JavaBundle.message("inspection.text.block.migration.concatenation.message"),
                                 new ReplaceWithTextBlockFix());
          return;
        }
        if (isOnTheFly && hasEscapedQuotes) {
          holder.registerProblem(expression,
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 ProblemHighlightType.INFORMATION, new ReplaceWithTextBlockFix());
        }
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiPolyadicExpression) return;
        boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
        if (!mySuggestLiteralReplacement && !quickFixOnly) return;
        PsiLiteralExpression literal = getLiteralExpression(expression);
        if (literal == null) return;
        String text = literal.getText();
        int newLineIdx = getNewLineIndex(text, 0);
        if (newLineIdx != -1 && getNewLineIndex(text, newLineIdx + 1) != -1) {
          holder.registerProblem(expression, quickFixOnly ? null : new TextRange(newLineIdx, newLineIdx + 2),
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 new ReplaceWithTextBlockFix());
          return;
        }
        if (isOnTheFly && getQuoteIndex(text) != -1) {
          holder.registerProblem(expression, 
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 ProblemHighlightType.INFORMATION, new ReplaceWithTextBlockFix());
        }
      }
    };
  }

  private static class ReplaceWithTextBlockFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.text.block.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(tryCast(descriptor.getPsiElement(), PsiExpression.class));
      if (expression == null) return;
      Document document = PsiDocumentManager.getInstance(project).getDocument(expression.getContainingFile());
      if (document == null) return;
      PsiLiteralExpression literalExpression = tryCast(expression, PsiLiteralExpression.class);
      if (literalExpression != null) {
        replaceWithTextBlock(new PsiExpression[]{literalExpression}, literalExpression);
        return;
      }
      PsiPolyadicExpression polyadicExpression = tryCast(expression, PsiPolyadicExpression.class);
      if (polyadicExpression == null || !isConcatenation(polyadicExpression)) return;
      replaceWithTextBlock(polyadicExpression.getOperands(), polyadicExpression);
    }

    private static void replaceWithTextBlock(PsiExpression @NotNull [] operands, @NotNull PsiExpression toReplace) {
      String[] lines = getContentLines(operands);
      if (lines == null) return;
      String textBlock = getTextBlock(lines);
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(toReplace, textBlock);
    }

    @NotNull
    private static String getTextBlock(String @NotNull [] lines) {
      lines = getTextBlockLines(lines);
      int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, true);
      // we need additional indent call only when significant trailing line is missing
      if (indent > 0 && lines.length > 0 && lines[lines.length - 1].endsWith("\n")) indent = 0;
      return "\"\"\"\n" + concatenateTextBlockLines(lines, indent) + "\"\"\"" + (indent > 0 ? ".indent(" + indent + ")" : "");
    }

    private static String @NotNull [] getTextBlockLines(String @NotNull [] lines) {
      String blockLines = PsiLiteralUtil.escapeTextBlockCharacters(StringUtil.join(lines), true, true, true);
      return blockLines.split("(?<=\n)");
    }

    private static String concatenateTextBlockLines(String @NotNull [] lines, int indent) {
      if (indent <= 0) return StringUtil.join(lines);
      return Arrays.stream(lines).map(line -> indent < line.length() ? line.substring(indent) : line).collect(Collectors.joining());
    }

    private static String @Nullable [] getContentLines(PsiExpression @NotNull [] operands) {
      String[] lines = new String[operands.length];
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = operands[i];
        PsiLiteralExpression literal = getLiteralExpression(operand);
        if (literal == null) return null;
        String line = getLiteralText(literal);
        if (line == null) return null;
        lines[i] = line;
      }
      return lines;
    }

    @Nullable
    private static String getLiteralText(@NotNull PsiLiteralExpression literal) {
      if (!literal.isTextBlock() && ExpressionUtils.hasStringType(literal)) return PsiLiteralUtil.getStringLiteralContent(literal);
      Object value = literal.getValue();
      return value == null ? null : value.toString();
    }
  }

  private static int getNewLineIndex(@NotNull String text, int start) {
    return getEscapedCharIndex(text, start, 'n');
  }
  
  private static int getQuoteIndex(@NotNull String text) {
    return getEscapedCharIndex(text, 0, '"');
  }

  private static int getEscapedCharIndex(@NotNull String text, int start, char escapedChar) {
    int i = start;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (i + 1 < text.length() && text.charAt(i + 1) == escapedChar) return i;
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
  private static PsiLiteralExpression getLiteralExpression(@NotNull PsiExpression expression) {
    PsiLiteralExpression literal = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiLiteralExpression.class);
    if (literal == null || literal.isTextBlock()) return null;
    return literal;
  }
}
