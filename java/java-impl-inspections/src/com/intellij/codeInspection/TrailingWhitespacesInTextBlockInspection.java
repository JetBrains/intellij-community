// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiTemplate;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiFragmentImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;


public final class TrailingWhitespacesInTextBlockInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.TEXT_BLOCKS);
  }
  
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTemplate(@NotNull PsiTemplate template) {
        super.visitTemplate(template);
        for (PsiFragment fragment : template.getFragments()) {
          if (!fragment.isTextBlock()) return;
          String suffix = (fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) ? "\"\"\"" : "\\{";
          if (checkTextBlock(fragment, suffix)) {
            return;
          }
        }
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (!expression.isTextBlock()) return;
        checkTextBlock(expression, "\"\"\"");
      }

      private boolean checkTextBlock(@NotNull PsiElement textBlock, @NotNull String suffix) {
        String text = textBlock.getText();
        String[] lines = text.split("\n", -1);
        int indent = getIndent(textBlock);
        if (indent == -1) return false;
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          if (i != 0) offset++; // count newline
          if (line.startsWith("\"\"\"")) {
            offset += line.length();
            continue;
          }
          int lineEnd;
          if (line.endsWith(suffix)) {
            if (suffix.equals("\\{")) return false;
            lineEnd = line.length() - suffix.length();
          }
          else {
            lineEnd = line.length();
          }
          boolean fragmentStart = StringUtil.startsWithChar(line, '}');
          if (fragmentStart ? lineEnd != 0 : lineEnd > indent) {
            char c = line.charAt(lineEnd - 1);
            if (c == ' ' || c == '\t') {
              for (int j = lineEnd - 2; j >= 0; j--) {
                c = line.charAt(j);
                if (c != ' ' && c != '\t' || j < indent && !fragmentStart) {
                  holder.registerProblem(textBlock, new TextRange(offset + j + 1, offset + lineEnd),
                                         JavaBundle.message("inspection.trailing.whitespaces.in.text.block.message"),
                                         createFixes());
                  return true;
                }
              }
            }
          }
          offset += line.length();
        }
        return false;
      }
    };
  }

  private static LocalQuickFix @NotNull [] createFixes() {
    return new LocalQuickFix[]{
      new ReplaceTrailingWhiteSpacesFix(JavaBundle.message("inspection.trailing.whitespaces.in.text.block.remove.whitespaces"),
                                        s -> removeWhitespaces(s)),
      new ReplaceTrailingWhiteSpacesFix(JavaBundle.message("inspection.trailing.whitespaces.in.text.block.replaces.whitespaces.with.escapes"),
                                        s -> replaceWhitespacesWithEscapes(s))
    };
  }

  private static int getIndent(@NotNull PsiElement textBlock) {
    return textBlock instanceof PsiFragment
           ? PsiFragmentImpl.getTextBlockFragmentIndent((PsiFragment)textBlock)
           : PsiLiteralUtil.getTextBlockIndent((PsiLiteralExpression)textBlock);
  }

  private static @NotNull String replaceWhitespacesWithEscapes(@NotNull String contentLine) {
    int len = contentLine.length();
    return switch (contentLine.charAt(len - 1)) {
      case ' ' -> contentLine.substring(0, len - 1) + "\\s";
      case '\t' -> contentLine.substring(0, len - 1) + "\\t";
      default -> contentLine;
    };
  }

  private static @NotNull String removeWhitespaces(@NotNull String contentLine) {
    for (int i = contentLine.length() - 1; i >= 0; i--) {
      char c = contentLine.charAt(i);
      if (c != ' ' && c != '\t') return contentLine.substring(0, i + 1);
    }
    return "";
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
    private final @IntentionFamilyName String myMessage;
    private final @NotNull Function<@NotNull String, String> myTransformation;

    private ReplaceTrailingWhiteSpacesFix(@NotNull @IntentionFamilyName String message,
                                          @NotNull Function<@NotNull String, String> transformation) {
      myMessage = message;
      myTransformation = transformation;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return myMessage;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiLiteralExpression expression) {
        if (!expression.isTextBlock()) return;
        String text = buildReplacementText(element, "\"\"\"", myTransformation);
        if (text == null) return;
        replaceTextBlock(expression, text);
      }
      else if (element instanceof PsiFragment fragment) {
        if (fragment.isTextBlock() && fragment.getParent() instanceof PsiTemplate template) {
          for (PsiFragment current : template.getFragments().reversed()) {
            String suffix = fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END ? "\"\"\"" : "\\{";
            String text = buildReplacementText(current, suffix, myTransformation);
            if (text == null) return;
            PsiReplacementUtil.replaceFragment(current, text);
            if (fragment == current) break;
          }
        }
      }
    }

    private static String buildReplacementText(PsiElement element, String suffix, Function<String, String> lineTransformation) {
      String[] lines = element.getText().split("\n", -1);
      int indent = getIndent(element);
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (line.startsWith("\"\"\"")) {
          result.append("\"\"\"\n");
          continue;
        }
        boolean last = (i == lines.length - 1);
        if (last) {
          if (suffix.equals("\\{")) return result.append(line).toString();
          line = line.substring(0, line.length() - suffix.length());
        }
        String transformed = line.isEmpty() ? line : lineTransformation.apply(line);
        if (transformed == null) return null;
        if (last && hasUnescapedLastQuote(transformed)) {
          result.append(transformed, 0, transformed.length() - 1).append("\\\"");
        }
        else if (transformed.isEmpty()) {
          result.append((line.length() < indent) ? line : line.substring(0, indent));
        } else {
          result.append(transformed);
        }
        result.append(last ? suffix : "\n");
      }
      return result.toString();
    }
  }
}