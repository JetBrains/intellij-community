// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class RedundantEscapeInRegexReplacementInspection extends BaseInspection {
  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    char c = (char)infos[0];
    return InspectionGadgetsBundle.message("redundant.escape.in.regex.replacement.problem.descriptor", c);
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    final boolean buildFix = (Boolean)infos[1];
    if (buildFix) {
      return LocalQuickFix.from(new RedundantEscapeInRegexReplacementFix());
    }
    else {
      return null;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantEscapeInRegexReplacementVisitor();
  }

  private static class RedundantEscapeInRegexReplacementVisitor extends BaseInspectionVisitor {

    private static final CallMatcher REGEX_REPLACEMENT_METHODS = CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "replaceAll", "replaceFirst"),
      CallMatcher.exactInstanceCall("java.util.regex.Matcher", "appendReplacement"),
      CallMatcher.exactInstanceCall("java.util.regex.Matcher", "replaceAll", "replaceFirst")
        .parameterTypes(CommonClassNames.JAVA_LANG_STRING)
    );

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!REGEX_REPLACEMENT_METHODS.matches(expression)) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final Object value = ExpressionUtils.computeConstantExpression(lastArgument);
      if (!(value instanceof String string)) {
        return;
      }
      boolean escaped = false;
      for (int i = 0, length = string.length(); i < length; i++) {
        char c = string.charAt(i);
        if (c == '\\') {
          escaped = !escaped;
        }
        else {
          if (escaped) {
            escaped = false;
            if (c == '$') continue; // $ needs escaping
            final TextRange range = ExpressionUtils.findStringLiteralRange(lastArgument, i - 1, i);
            if (range != null) {
              registerErrorAtOffset(lastArgument, range.getStartOffset(), range.getLength(), c, Boolean.TRUE);
            }
            else {
              registerError(lastArgument, c, Boolean.FALSE);
              return;
            }
          }
        }
      }
    }
  }

  private static class RedundantEscapeInRegexReplacementFix extends PsiUpdateModCommandAction<PsiLiteralExpression> {
    RedundantEscapeInRegexReplacementFix() {
      super(PsiLiteralExpression.class);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiLiteralExpression literal, @NotNull ModPsiUpdater updater) {
      final TextRange range = context.selection().shiftLeft(literal.getTextRange().getStartOffset());
      final String text = literal.getText();
      final int length = text.length();
      final int start = range.getStartOffset();
      final int end = range.getEndOffset();

      if (start >= length || end >= length || !StringUtil.unescapeStringCharacters(text.substring(start, end)).equals("\\")) return;
      final String newText = text.substring(0, start) + text.substring(end);
      PsiReplacementUtil.replaceExpression(literal, newText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.escape.in.regex.replacement.quickfix");
    }
  }
}
