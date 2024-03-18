// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class StringLiteralManipulator extends AbstractElementManipulator<PsiLiteralExpression> {
  @Override
  public PsiLiteralExpression handleContentChange(@NotNull PsiLiteralExpression expr, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = expr.getText();
    if (oldText.startsWith("\"")) {
      newContent = StringUtil.escapeStringCharacters(newContent);
    }
    else if (oldText.startsWith("'") && newContent.length() <= 1) {
      newContent = newContent.length() == 1 && newContent.charAt(0) == '\''? "\\'" : newContent;
    }
    else {
      throw new IncorrectOperationException("cannot handle content change for: " + oldText + ", expr: " + expr);
    }

    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final PsiExpression newExpr = JavaPsiFacade.getElementFactory(expr.getProject()).createExpressionFromText(newText, null);
    return (PsiLiteralExpression)expr.replace(newExpr);
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull final PsiLiteralExpression element) {
    return getValueRange(element);
  }

  @NotNull
  public static TextRange getValueRange(@NotNull PsiLiteralExpression expression) {
    if (expression instanceof PsiLanguageInjectionHost) {
      return ((PsiLanguageInjectionHost)expression).createLiteralTextEscaper().getRelevantTextRange();
    }
    // Normally, we go to the previous branch. Probably third-party implementations or ClsLiteralExpressionImpl may reach here
    if (expression.isTextBlock()) {
      throw new UnsupportedOperationException();
    }
    // avoid calling PsiLiteralExpression.getValue(): it allocates new string, it returns null for invalid escapes
    int length = expression.getTextLength();
    final PsiType type = expression.getType();
    boolean isQuoted = PsiTypes.charType().equals(type) || type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    return isQuoted ? new TextRange(1, Math.max(1, length - 1)) : TextRange.from(0, length);
  }
}