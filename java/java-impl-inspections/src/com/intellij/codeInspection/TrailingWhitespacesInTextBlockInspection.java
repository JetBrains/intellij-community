// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;


public final class TrailingWhitespacesInTextBlockInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitFragment(@NotNull PsiFragment fragment) {
        super.visitFragment(fragment);
        if (!fragment.isTextBlock()) return;
        checkTextBlock(fragment, (fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) ? "\"\"\"" : "\\{");
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (!expression.isTextBlock()) return;
        checkTextBlock(expression, "\"\"\"");
      }

      private void checkTextBlock(@NotNull PsiElement textBlock, String suffix) {
        String textBlockText = textBlock.getText();
        String[] lines = textBlockText.split("\n", -1);
        if (lines.length < 2) return;
        int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, false);
        if (indent == -1) return;
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          if (i != 0) offset++;
          if (line.isBlank() || line.startsWith("\"\"\"")) {
            for (int j = 3, length = line.length(); j < length; j++) {
              char c = line.charAt(j);
              if (!PsiLiteralUtil.isTextBlockWhiteSpace(c)) return;
            }
            offset += line.length();
            continue;
          }
          int lineEnd = line.endsWith(suffix)? line.length() - suffix.length() : line.length();
          if (lineEnd == 0) continue;
          char c = line.charAt(lineEnd - 1);
          if (c == ' ' || c == '\t') {
            for (int j = lineEnd - 2; j >= 0; j--) {
              c = line.charAt(j);
              if (c != ' ' && c != '\t') {
                holder.registerProblem(textBlock, new TextRange(offset + j + 1, offset + j + 2),
                                       JavaBundle.message("inspection.trailing.whitespaces.in.text.block.message"),
                                       createFixes());
                return;
              }
            }
          }
          offset += line.length();
        }
      }
    };
  }

  private static LocalQuickFix @NotNull [] createFixes() {
    return new LocalQuickFix[]{
      new ReplaceTrailingWhiteSpacesFix("inspection.trailing.whitespaces.in.text.block.remove.whitespaces", c -> removeWhitespaces(c)),
      new ReplaceTrailingWhiteSpacesFix("inspection.trailing.whitespaces.in.text.block.replaces.whitespaces.with.escapes",
                                        c -> replaceWhitespacesWithEscapes(c))
    };
  }

  private static @NotNull String replaceWhitespacesWithEscapes(@NotNull TransformationContext context) {
    String contentLine = context.text();
    int len = contentLine.length();
    char c = contentLine.charAt(len - 1);
    return switch (c) {
      case ' ' -> contentLine.substring(0, len - 1) + "\\s";
      case '\t' -> contentLine.substring(0, len - 1) + "\\t";
      default -> contentLine;
    };
  }

  private static @NotNull String removeWhitespaces(@NotNull TransformationContext context) {
    String contentLine = context.text();
    int j;
    for (j = contentLine.length() - 2; j >= 0; j--) {
      char c = contentLine.charAt(j);
      if (c != ' ' && c != '\t') break;
    }
    String result = contentLine.substring(0, j + 1);
    if (context.isEnd() && hasUnescapedLastQuote(result)) {
      result = result.substring(0, result.length() - 1) + "\\\"";
    }
    return result;
  }

  public static boolean hasUnescapedLastQuote(String text) {
    if (!text.endsWith("\"")) {
      return false;
    }
    int i = 0;
    int countBackSlash = 0;
    int length = text.length();
    while (i < length - 1) {
      int nextIdx = PsiLiteralUtil.parseBackSlash(text, i);
      if (nextIdx != -1) {
        countBackSlash++;
        i = nextIdx;
      }
      else {
        countBackSlash = 0;
        i++;
      }
    }
    return countBackSlash % 2 == 0;
  }

  static void replaceTextBlock(@NotNull PsiLiteralExpression toReplace, @NotNull String newTextBlock) {
    Project project = toReplace.getProject();
    PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newTextBlock, toReplace);
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(() -> toReplace.replace(replacement));
  }

  private static class ReplaceTrailingWhiteSpacesFix extends PsiUpdateModCommandQuickFix {
    private final String myMessage;
    private final @NotNull Function<@NotNull TransformationContext, String> myTransformation;

    private ReplaceTrailingWhiteSpacesFix(@NotNull String message,
                                          @NotNull Function<@NotNull TransformationContext, String> transformation) {
      myMessage = message;
      myTransformation = transformation;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return JavaBundle.message(myMessage);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiLiteralExpression expression) {
        if (!expression.isTextBlock()) return;
        String text = buildReplacementText(element, myTransformation);
        if (text == null) return;
        replaceTextBlock(expression, text);
      }
      else if (element instanceof PsiFragment fragment) {
        if (!fragment.isTextBlock()) return;
        String text = buildReplacementText(element, myTransformation);
        if (text == null) return;
        PsiReplacementUtil.replaceFragment(fragment, text);
      }
    }

    private static @Nullable String buildReplacementText(PsiElement element,
                                                         @NotNull Function<TransformationContext, String> lineTransformation) {
      String[] lines = element.getText().split("\n", -1);
      String suffix =
        !(element instanceof PsiFragment fragment) || fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END
        ? "\"\"\""
        : "\\{";
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (line.startsWith("\"\"\"")) {
          result.append("\"\"\"\n");
          continue;
        }
        boolean last = i == lines.length - 1;
        if (last) {
          line = line.substring(0, line.length() - suffix.length());
        }
        if (!isContentLineEndsWithWhitespace(line)) {
          result.append(line);
        }
        else {
          CharSequence transformed = lineTransformation.apply(new TransformationContext(line, last));
          if (transformed == null) return null;
          result.append(transformed);
        }
        result.append(last ? suffix : "\n");
      }
      return result.toString();
    }

    private static boolean isContentLineEndsWithWhitespace(@NotNull String line) {
      if (line.isBlank()) return false;
      char lastChar = line.charAt(line.length() - 1);
      return lastChar == ' ' || lastChar == '\t';
    }
  }

  private record TransformationContext(@NotNull String text, boolean isEnd) {}
}