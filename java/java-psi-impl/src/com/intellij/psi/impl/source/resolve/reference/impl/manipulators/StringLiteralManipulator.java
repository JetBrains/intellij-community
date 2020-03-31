// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class StringLiteralManipulator extends AbstractElementManipulator<PsiLiteralExpression> {
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
  public static TextRange getValueRange(@NotNull PsiLiteralExpression element) {
    int length = element.getTextLength();
    boolean isQuoted;
    if (element instanceof PsiLiteralExpressionImpl) {
      // avoid calling getValue(): it allocates new string, it returns null for invalid escapes
      IElementType type = ((PsiLiteralExpressionImpl)element).getLiteralElementType();
      if (type == JavaTokenType.TEXT_BLOCK_LITERAL) {
        final String text = element.getText();
        int startOffset = findBlockStart(text);
        return startOffset < 0
               ? new TextRange(0, length)
               : new TextRange(startOffset, length - (text.endsWith("\"\"\"") ? 3 : 0));
      }
      isQuoted = type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.CHARACTER_LITERAL;
    }
    else {
      Object value = element.getValue();
      isQuoted = value instanceof String || value instanceof Character;
    }
    return isQuoted ? new TextRange(1, Math.max(1, length - 1)) : TextRange.from(0, length);
  }

  private static int findBlockStart(String text) {
    if (!text.startsWith("\"\"\"")) return -1;
    final int length = text.length();
    for (int i = 3; i < length; i++) {
      final char c = text.charAt(i);
      if (c == '\n') return i + 1;
      if (!Character.isWhitespace(c)) return -1;
    }
    return -1;
  }
}