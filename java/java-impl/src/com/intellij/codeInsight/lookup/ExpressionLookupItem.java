/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.*;
import com.intellij.util.Icons;

/**
 * @author peter
*/
public class ExpressionLookupItem extends LookupItem<PsiExpression> implements TypedLookupItem {
  public ExpressionLookupItem(final PsiExpression expression) {
    super(expression, expression.getText());

    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement element = referenceExpression.resolve();
      if (element != null) {
        setIcon(element.getIcon(0));
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      setIcon(Icons.METHOD_ICON);
    }
  }

  public PsiType getType() {
    return getObject().getType();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof ExpressionLookupItem && getLookupString().equals(((ExpressionLookupItem)o).getLookupString());
  }

  @Override
  public int hashCode() {
    return getLookupString().hashCode();
  }
}