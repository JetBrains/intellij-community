// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiFragmentImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryStringEscapeInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean reportChars = false;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.string.escape.problem.descriptor", infos[1]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportChars", InspectionGadgetsBundle.message("inspection.unnecessary.string.escape.report.char.literals.option")));
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    final String expressionText = (String)infos[0];
    return new UnnecessaryStringEscapeFix(expressionText);
  }

  private static class UnnecessaryStringEscapeFix extends PsiUpdateModCommandQuickFix {

    private final String myText;

    UnnecessaryStringEscapeFix(String text) {
      myText = text;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.string.escape.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final String text = element.getText();
      if (!myText.equals(text)) {
        return;
      }
      if (element instanceof PsiFragment fragment) {
        if (fragment.isTextBlock()) {
          int indent = PsiFragmentImpl.getTextBlockFragmentIndent(fragment);
          PsiReplacementUtil.replaceFragment(fragment, buildNewTextBlockText(text, indent));
        }
        else {
          PsiReplacementUtil.replaceFragment(fragment, buildNewStringText(text));
        }
      }
      else if (element instanceof PsiLiteralExpression literalExpression) {
        final PsiType type = literalExpression.getType();
        if (type == null) {
          return;
        }

        if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          if (literalExpression.isTextBlock()) {
            final int indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
            if (indent < 0)  return;
            final String newTextBlockTest = buildNewTextBlockText(text, indent);
            final Document document = element.getContainingFile().getFileDocument();
            final TextRange replaceRange = element.getTextRange();
            document.replaceString(replaceRange.getStartOffset(), replaceRange.getEndOffset(), newTextBlockTest);
          }
          else {
            PsiReplacementUtil.replaceExpression(literalExpression, buildNewStringText(text));
          }
        }
        else if (PsiTypes.charType().equals(type) && text.equals("'\\\"'")) {
          PsiReplacementUtil.replaceExpression(literalExpression, "'\"'");
        }
      }
    }

    private static String buildNewStringText(String text) {
      final StringBuilder newExpression = new StringBuilder();
      boolean escaped = false;
      final int length = text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        if (escaped) {
          if (c != '\'') newExpression.append('\\');
          newExpression.append(c);
          escaped = false;
        }
        else if (c == '\\') {
          escaped = true;
        }
        else {
          newExpression.append(c);
        }
      }
      return newExpression.toString();
    }

    private static @NotNull String buildNewTextBlockText(String text, int indent) {
      final StringBuilder newExpression = new StringBuilder();
      int offset = 0;
      int end = text.endsWith("\"\"\"") ? text.length() - 3 : text.length() - 2;
      int start = findUnnecessaryTextBlockEscapes(text, text.startsWith("\"\"\"") ? 4 : 1, end);
      while (start >= 0) {
        newExpression.append(text, offset, start);
        offset = start + 2;
        final @NonNls String escape = text.substring(start, offset);
        if ("\\n".equals(escape)) {
          newExpression.append('\n').append(StringUtil.repeatSymbol(' ', indent));
        }
        else {
          newExpression.append(escape.charAt(1));
        }
        start = findUnnecessaryTextBlockEscapes(text, offset, end);
      }
      newExpression.append(text.substring(offset));
      return newExpression.toString();
    }
  }

  static int findUnnecessaryStringEscapes(String text, int start) {
    boolean slash = false;
    final int max = text.length() - 1; // skip closing "
    for (int i = start; i < max; i++) {
      final char c = text.charAt(i);
      if (slash) {
        slash = false;
        if (c == '\'') return i - 1;
      }
      else if (c == '\\') slash = true;
    }
    return -1;
  }

  static int findUnnecessaryTextBlockEscapes(String text, int start, int end) {
    boolean slash = false;
    boolean ws = false;
    int doubleQuotes = 0;
    for (int i = start; i < end; i++) {
      final char ch = text.charAt(i);
      if (ch == '\\') slash = !slash;
      else if (ch == ' ' || ch == '\t') ws = true;
      else {
        if (slash) {
          if (ch == 'n') {
            if (!ws) return i - 1;
          }
          else if (ch == '\'') {
            return i - 1;
          }
          else if (ch == '"' && doubleQuotes < 2) {
            if (i == end - 1) return -1;
            if (i == end - 2) return i - 1;
            if (doubleQuotes == 1) {
              if (!text.startsWith("\"", i + 1) && !text.startsWith("\\\"", i + 1)) return i - 1;
            }
            else if (!text.startsWith("\"\"", i + 1) && !text.startsWith("\\\"\"", i + 1) &&
                     !text.startsWith("\"\\\"", i + 1) && !text.startsWith("\\\"\\\"", i + 1)) {
              return i - 1;
            }
          }
          doubleQuotes = 0;
        }
        else if (ch == '"') doubleQuotes++;
        else doubleQuotes = 0;
        slash = false;
        ws = false;
      }
    }
    return -1;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantStringEscapeVisitor();
  }

  private class RedundantStringEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFragment(@NotNull PsiFragment fragment) {
      super.visitFragment(fragment);
      HighlightInfo.Builder error = HighlightUtil.checkFragmentError(fragment);
      if (error != null) {
        return;
      }

      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getCurrentFile().getProject());
      final String text = manager.getUnescapedText(fragment);
      if (fragment.isTextBlock()) {
        int end = fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END ? text.length() - 3 : text.length() - 2;
        int start = findUnnecessaryTextBlockEscapes(text, 1, end);
        while (start >= 0) {
          registerErrorAtOffset(fragment, start, 2, text, text.substring(start, start + 2));
          start = findUnnecessaryTextBlockEscapes(text, start + 2, end);
        }
      }
      else {
        int start = findUnnecessaryStringEscapes(text, 1);
        while (start >= 0) {
          registerErrorAtOffset(fragment, start, 2, text, text.substring(start, start + 2));
          start = findUnnecessaryStringEscapes(text, start + 2);
        }
      }
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      HighlightInfo.Builder parsingError =
        HighlightUtil.checkLiteralExpressionParsingError(expression, PsiUtil.getLanguageLevel(expression), null, null);
      if (parsingError != null) {
        return;
      }
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getCurrentFile().getProject());
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        final String text = manager.getUnescapedText(expression);
        if (expression.isTextBlock()) {
          int end = text.length() - 3;
          int start = findUnnecessaryTextBlockEscapes(text, 4, end);
          while (start >= 0) {
            registerErrorAtOffset(expression, start, 2, text, text.substring(start, start + 2));
            start = findUnnecessaryTextBlockEscapes(text, start + 2, end);
          }
        }
        else {
          int start = findUnnecessaryStringEscapes(text, 1);
          while (start >= 0) {
            registerErrorAtOffset(expression, start, 2, text, text.substring(start, start + 2));
            start = findUnnecessaryStringEscapes(text, start + 2);
          }
        }
      }
      else if (reportChars && PsiTypes.charType().equals(type)) {
        final String text = manager.getUnescapedText(expression);
        if ("'\\\"'".equals(text)) {
          registerErrorAtOffset(expression, 1, 2, text, text.substring(1, 3));
        }
      }
    }
  }
}
