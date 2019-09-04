// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

import static com.intellij.util.ObjectUtils.tryCast;

public class TextBlockBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        PsiLiteralExpressionImpl literalExpression = tryCast(expression, PsiLiteralExpressionImpl.class);
        if (literalExpression == null) return;
        if (literalExpression.getLiteralElementType() != JavaTokenType.TEXT_BLOCK_LITERAL || literalExpression.getTextBlockText() == null) {
          return;
        }
        holder.registerProblem(literalExpression, InspectionsBundle.message("inspection.text.block.backward.migration.message"),
                               new ReplaceWithRegularStringLiteralFix());
      }
    };
  }

  private static class ReplaceWithRegularStringLiteralFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.replace.with.regular.string.literal.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpressionImpl literalExpression = tryCast(descriptor.getPsiElement(), PsiLiteralExpressionImpl.class);
      if (literalExpression == null || literalExpression.getLiteralElementType() != JavaTokenType.TEXT_BLOCK_LITERAL) return;
      String text = literalExpression.getTextBlockText();
      if (text == null) return;
      String replacement = convertToConcatenation(text);
      PsiReplacementUtil.replaceExpression(literalExpression, replacement, new CommentTracker());
    }

    @NotNull
    private static String convertToConcatenation(@NotNull String text) {
      if (text.isEmpty()) return "\"\"";
      StringJoiner joiner = new StringJoiner(" +\n");
      String[] lines = text.split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        boolean addNewLine = i != lines.length - 1;
        if (!addNewLine && line.isEmpty()) break;
        joiner.add("\"" + escapeQuotes(line) + (addNewLine ? "\\n\"" : "\""));
      }
      return joiner.toString();
    }

    @NotNull
    private static String escapeQuotes(@NotNull String str) {
      StringBuilder sb = new StringBuilder(str.length());
      int nSlashes = 0;
      int idx = 0;
      while (idx < str.length()) {
        char c = str.charAt(idx);
        int nextIdx = parseBackSlash(str, idx);
        if (nextIdx > 0) {
          nSlashes++;
        }
        else {
          if (c == '\"' && nSlashes % 2 == 0) {
            sb.append('\\');
          }
          nSlashes = 0;
          nextIdx = idx + 1;
        }
        sb.append(c);
        idx = nextIdx;
      }
      return sb.toString();
    }

    private static int parseBackSlash(@NotNull String str, int idx) {
      char c = str.charAt(idx);
      if (c != '\\') return -1;
      int nextIdx = parseHexBackSlash(str, idx);
      if (nextIdx > 0) return nextIdx;
      nextIdx = parseOctalBackSlash(str, idx);
      return nextIdx > 0 ? nextIdx : idx + 1;
    }

    private static int parseHexBackSlash(@NotNull String str, int idx) {
      int next = idx + 1;
      if (next >= str.length() || str.charAt(next) != 'u') return -1;
      while (str.charAt(next) == 'u') {
        next++;
      }
      if (next + 3 >= str.length()) return -1;
      try {
        int code = Integer.parseInt(str.substring(next, next + 4), 16);
        if (code == '\\') return next + 4;
      }
      catch (NumberFormatException ignored) {
      }
      return -1;
    }

    private static int parseOctalBackSlash(@NotNull String str, int idx) {
      int next = idx + 1;
      if (next + 2 >= str.length()) return -1;
      try {
        int code = Integer.parseInt(str.substring(next, next + 3), 8);
        if (code == '\\') return next + 3;
      }
      catch (NumberFormatException ignored) {
      }
      return -1;
    }
  }
}
