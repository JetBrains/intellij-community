// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;

public final class TextBlockMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean mySuggestLiteralReplacement = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("mySuggestLiteralReplacement", JavaBundle.message("inspection.text.block.migration.suggest.literal.replacement")));
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.TEXT_BLOCKS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
        if (!ExpressionUtils.hasStringType(expression)) return;
        int nNewLines = 0;
        TextRange firstNewLineTextRange = null;
        for (PsiExpression operand : expression.getOperands()) {
          PsiLiteralExpression literal = getLiteralExpression(operand);
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
        boolean hasComments = ContainerUtil.exists(expression.getChildren(), child -> child instanceof PsiComment);
        boolean reportWarning = nNewLines > 1 && !hasComments;
        if (reportWarning) {
          boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
          holder.registerProblem(expression, quickFixOnly ? null : firstNewLineTextRange,
                                 JavaBundle.message("inspection.text.block.migration.concatenation.message"),
                                 new ReplaceWithTextBlockFix());
        }
        else if (isOnTheFly) {
          holder.registerProblem(expression,
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 ProblemHighlightType.INFORMATION, new ReplaceWithTextBlockFix());
        }
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiPolyadicExpression) return;
        if (!ExpressionUtils.hasStringType(expression) || expression.isTextBlock()) return;
        String text = expression.getText();
        int newLineIdx = getNewLineIndex(text, 0);
        if (mySuggestLiteralReplacement && newLineIdx != -1 && getNewLineIndex(text, newLineIdx + 1) != -1) {
          boolean quickFixOnly = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), expression);
          holder.registerProblem(expression, quickFixOnly ? null : new TextRange(newLineIdx, newLineIdx + 2),
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 new ReplaceWithTextBlockFix());
        }
        else if (isOnTheFly) {
          holder.registerProblem(expression, 
                                 JavaBundle.message("inspection.text.block.migration.string.message"),
                                 ProblemHighlightType.INFORMATION, new ReplaceWithTextBlockFix());
        }
      }
    };
  }

  private static class ReplaceWithTextBlockFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.text.block.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(tryCast(element, PsiExpression.class));
      if (expression == null) return;
      Document document = expression.getContainingFile().getViewProvider().getDocument();
      if (document == null) return;
      if (expression instanceof PsiLiteralExpression literalExpression) {
        replaceWithTextBlock(literalExpression, new PsiExpression[]{literalExpression});
      }
      else if (expression instanceof PsiPolyadicExpression polyadicExpression && ExpressionUtils.hasStringType(polyadicExpression)) {
        replaceWithTextBlock(polyadicExpression, polyadicExpression.getOperands());
      }
    }

    private static void replaceWithTextBlock(@NotNull PsiExpression toReplace, PsiExpression @NotNull [] operands) {
      String[] lines = getContentLines(operands);
      if (lines == null) return;
      String content = String.join("", lines);
      PsiExpression emptyTextBlock = JavaPsiFacade.getElementFactory(toReplace.getProject()).createExpressionFromText("\"\"\"\n\"\"\"", null);
      PsiElement inDocument = new CommentTracker().replaceAndRestoreComments(toReplace, emptyTextBlock);
      ElementManipulators.getManipulator(inDocument).handleContentChange(inDocument, content);
    }

    private static String @Nullable [] getContentLines(PsiExpression @NotNull [] operands) {
      String[] lines = new String[operands.length];
      PsiLiteralExpression previous = null;
      for (int i = 0; i < operands.length; i++) {
        PsiLiteralExpression literal = getLiteralExpression(operands[i]);
        if (literal == null) return null;
        String line = getLiteralText(literal);
        if (line == null) return null;
        if (previous != null && !onSameLine(previous, literal) && !lines[i - 1].endsWith("\\n")) {
          lines[i - 1] += "\\\n";
        }
        previous = literal;
        lines[i] = line;
      }
      return lines;
    }

    private static boolean onSameLine(@NotNull PsiElement e1, @NotNull PsiElement e2) {
      PsiFile containingFile = e1.getContainingFile();
      if (containingFile != e2.getContainingFile()) {
        throw new IllegalArgumentException();
      }
      Document document = containingFile.getViewProvider().getDocument();
      return document != null && document.getLineNumber(e1.getTextOffset()) == document.getLineNumber(e2.getTextOffset());
    }

    private static @Nullable String getLiteralText(@NotNull PsiLiteralExpression literal) {
      if (!literal.isTextBlock() && ExpressionUtils.hasStringType(literal)) return PsiLiteralUtil.getStringLiteralContent(literal);
      Object value = literal.getValue();
      return value == null ? null : value.toString();
    }
  }

  private static int getNewLineIndex(@NotNull String text, int start) {
    return getEscapedCharIndex(text, start, 'n');
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

  private static @Nullable PsiLiteralExpression getLiteralExpression(@NotNull PsiExpression expression) {
    PsiLiteralExpression literal = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiLiteralExpression.class);
    return (literal == null || literal.isTextBlock()) ? null : literal;
  }
}
