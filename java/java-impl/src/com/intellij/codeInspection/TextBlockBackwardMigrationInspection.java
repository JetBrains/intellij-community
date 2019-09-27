// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

import static com.intellij.util.ObjectUtils.tryCast;

public class TextBlockBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
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
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      if (file == null) return;
      CodeStyleSettings tempSettings = CodeStyle.getSettings(file);
      tempSettings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_BINARY_OPERATION = true;
      CodeStyleManager manager = CodeStyleManager.getInstance(literalExpression.getProject());
      CodeStyle.doWithTemporarySettings(project, tempSettings, () -> {
        PsiElement result = new CommentTracker().replaceAndRestoreComments(literalExpression, replacement);
        manager.reformat(result);
      });
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
        joiner.add("\"" + PsiLiteralUtil.escapeQuotes(line) + (addNewLine ? "\\n\"" : "\""));
      }
      return joiner.toString();
    }
  }
}
