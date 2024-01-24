// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

import static com.intellij.util.ObjectUtils.tryCast;

public final class TextBlockBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        if (!expression.isTextBlock() || PsiLiteralUtil.getTextBlockText(expression) == null ||
            expression.getParent() instanceof PsiTemplateExpression) {
          return;
        }
        holder.registerProblem(expression, JavaBundle.message("inspection.text.block.backward.migration.message"),
                               new ReplaceWithRegularStringLiteralFix());
      }
    };
  }

  private static class ReplaceWithRegularStringLiteralFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.regular.string.literal.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiLiteralExpression literalExpression = tryCast(element, PsiLiteralExpression.class);
      if (literalExpression == null || !literalExpression.isTextBlock()) return;
      String text = PsiLiteralUtil.getTextBlockText(literalExpression);
      if (text == null) return;
      String replacement = convertToConcatenation(text);
      PsiFile file = element.getContainingFile();
      if (file == null) return;
      CodeStyleSettings tempSettings = CodeStyle.getSettings(file);
      tempSettings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_BINARY_OPERATION = true;
      CodeStyleManager manager = CodeStyleManager.getInstance(literalExpression.getProject());
      CodeStyle.runWithLocalSettings(project, tempSettings, () -> {
        PsiElement result = new CommentTracker().replaceAndRestoreComments(literalExpression, replacement);
        manager.reformat(result);
      });
    }

    @NotNull
    private static String convertToConcatenation(@NotNull String text) {
      if (text.isEmpty()) return "\"\"";
      StringJoiner joiner = new StringJoiner(" +\n");
      String[] lines = getTextBlockLines(text).split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        boolean addNewLine = i != lines.length - 1;
        if (!addNewLine && line.isEmpty()) break;
        joiner.add("\"" + line + (addNewLine ? "\\n\"" : "\""));
      }
      return joiner.toString();
    }

    @NotNull
    private static String getTextBlockLines(@NotNull String text) {
      int length = text.length();
      StringBuilder result = new StringBuilder(length);
      int i = 0;
      while (i < length) {
        int nSlashes = 0;
        int next = i;
        while (next < length && (next = PsiLiteralUtil.parseBackSlash(text, next)) != -1) {
          nSlashes++;
          i = next;
        }
        if (i >= length) {
          result.append(StringUtil.repeatSymbol('\\', nSlashes));
          break;
        }
        next = parseQuote(i, text, nSlashes, result);
        if (next != -1) {
          i = next;
          continue;
        }
        if (nSlashes != 0) {
          i = parseEscapedChar(i, text, nSlashes, result);
        }
        else {
          result.append(text.charAt(i));
          i++;
        }
      }
      return result.toString();
    }

    private static int parseEscapedChar(int i, @NotNull String text, int nSlashes, @NotNull StringBuilder result) {
      int next = parseEscapedSpace(i, text, nSlashes, result);
      if (next != -1) return next;
      next = parseEscapedLineBreak(i, text, nSlashes, result);
      if (next != -1) return next;
      result.append(StringUtil.repeatSymbol('\\', nSlashes)).append(text.charAt(i));
      return i + 1;
    }

    private static int parseEscapedSpace(int i, @NotNull String text, int nSlashes, @NotNull StringBuilder result) {
      char c = text.charAt(i);
      if (c == 's' && nSlashes % 2 != 0) {
        result.append(StringUtil.repeatSymbol('\\', nSlashes - 1)).append(' ');
        return i + 1;
      }
      if (StringUtil.startsWith(text, i, "040") && nSlashes % 2 != 0) {
        result.append(StringUtil.repeatSymbol('\\', nSlashes - 1)).append(' ');
        return i + 3;
      }
      return -1;
    }

    private static int parseEscapedLineBreak(int i, @NotNull String text, int nSlashes, @NotNull StringBuilder result) {
      char c = text.charAt(i);
      if (c == '\n' && nSlashes % 2 != 0) {
        result.append(StringUtil.repeatSymbol('\\', nSlashes - 1));
        return i + 1;
      }
      return -1;
    }

    private static int parseQuote(int i, @NotNull String text, int nSlashes, @NotNull StringBuilder result) {
      char c = text.charAt(i);
      if (c != '"') return -1;
      if (nSlashes % 2 == 0) nSlashes++;
      result.append(StringUtil.repeatSymbol('\\', nSlashes)).append(c);
      return i + 1;
    }
  }
}
