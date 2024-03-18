// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFragment;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class FragmentManipulator extends AbstractElementManipulator<PsiFragment> {
  @Override
  public PsiFragment handleContentChange(@NotNull PsiFragment expr, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
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
    return (PsiFragment)expr.replace(newExpr);
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull final PsiFragment element) {
    return getValueRange(element);
  }

  @NotNull
  public static TextRange getValueRange(@NotNull PsiFragment fragment) {
    return fragment.createLiteralTextEscaper().getRelevantTextRange();
  }
}