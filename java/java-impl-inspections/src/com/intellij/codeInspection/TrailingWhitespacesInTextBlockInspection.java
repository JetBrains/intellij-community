// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;


public class TrailingWhitespacesInTextBlockInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
          String[] lines = PsiLiteralUtil.getTextBlockLines(expression);
        if (lines == null) return;
        int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, false);
        if (indent == -1) return;
        int start = expression.getText().indexOf('\n');
        if (start == -1) return;
        start++;
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          if (i != 0) start++;
          if (line.isBlank()) {
            start += line.length();
            continue;
          }
          char c = line.charAt(line.length() - 1);
          if (c == ' ' || c == '\t') {
            for (int j = line.length() - 2; j >= 0; j--) {
              c = line.charAt(j);
              if (c != ' ' && c != '\t') {
                holder.registerProblem(expression, new TextRange(start + j + 1, start + j + 2),
                                       JavaBundle.message("inspection.trailing.whitespaces.in.text.block.message"),
                                       createFixes());
                return;
              }
            }
          }
          start += line.length();
        }
      }
    };
  }

  private static LocalQuickFix @NotNull [] createFixes() {
    return new LocalQuickFix[]{
      new ReplaceTrailingWhiteSpacesFix("inspection.trailing.whitespaces.in.text.block.remove.whitespaces", l -> removeWhitespaces(l)),
      new ReplaceTrailingWhiteSpacesFix("inspection.trailing.whitespaces.in.text.block.replaces.whitespaces.with.escapes",
                                        l -> replaceWhitespacesWithEscapes(l))
    };
  }

  private static @NotNull String replaceWhitespacesWithEscapes(@NotNull String contentLine) {
    int j;
    int len = contentLine.length();
    for (j = len - 2; j >= 0; j--) {
      char c = contentLine.charAt(j);
      if (c != ' ' && c != '\t') break;
    }
    j++;
    StringBuilder transformed = new StringBuilder(j + (len - j) * 4);
    transformed.append(contentLine, 0, j);
    for (; j < len; j++) {
      if (contentLine.charAt(j) == ' ') {
        transformed.append("\\040");
      }
      else {
        transformed.append("\\t");
      }
    }
    return transformed.toString();
  }

  private static @NotNull String removeWhitespaces(@NotNull String contentLine) {
    int j;
    for (j = contentLine.length() - 2; j >= 0; j--) {
      char c = contentLine.charAt(j);
      if (c != ' ' && c != '\t') break;
    }
    return contentLine.substring(0, j + 1);
  }

  static void replaceTextBlock(@NotNull Project project, @NotNull PsiLiteralExpression toReplace, @NotNull String newTextBlock) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiExpression replacement = elementFactory.createExpressionFromText(newTextBlock, toReplace);
    CodeStyleManager manager = CodeStyleManager.getInstance(project);
    manager.performActionWithFormatterDisabled(() -> toReplace.replace(replacement));
  }

  private static class ReplaceTrailingWhiteSpacesFix implements LocalQuickFix {
    private final String myMessage;
    private final @NotNull Function<? super @NotNull String, ? extends @Nullable CharSequence> myTransformation;

    private ReplaceTrailingWhiteSpacesFix(@NotNull String message,
                                          @NotNull Function<? super @NotNull String, ? extends @Nullable CharSequence> transformation) {
      myMessage = message;
      myTransformation = transformation;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return JavaBundle.message(myMessage);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression expression = tryCast(descriptor.getPsiElement(), PsiLiteralExpression.class);
      if (expression == null || !expression.isTextBlock()) return;
      String[] lines = PsiLiteralUtil.getTextBlockLines(expression);
      if (lines == null) return;
      String newTextBlock = transformTextBlockLines(lines, myTransformation);
      if (newTextBlock == null) return;
      replaceTextBlock(project, expression, newTextBlock);
    }

    private static @Nullable String transformTextBlockLines(String @NotNull [] lines,
                                                            @NotNull Function<? super String, ? extends @Nullable CharSequence> lineTransformation) {
      StringBuilder newTextBlock = new StringBuilder();
      newTextBlock.append("\"\"\"\n");
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (i != 0) newTextBlock.append('\n');
        if (!isContentLineEndsWithWhitespace(line)) {
          newTextBlock.append(line);
          continue;
        }
        CharSequence transformed = lineTransformation.apply(line);
        if (transformed == null) return null;
        newTextBlock.append(transformed);
      }
      newTextBlock.append("\"\"\"");

      return newTextBlock.toString();
    }

    private static boolean isContentLineEndsWithWhitespace(@NotNull String line) {
      if (line.isBlank()) return false;
      char lastChar = line.charAt(line.length() - 1);
      return lastChar == ' ' || lastChar == '\t';
    }
  }
}
