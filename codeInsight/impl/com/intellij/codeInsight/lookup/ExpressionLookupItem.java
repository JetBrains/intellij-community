/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiExpression;

/**
 * @author peter
*/
class ExpressionLookupItem extends LookupItem<PsiExpression> {
public ExpressionLookupItem(final PsiExpression expression) {
  super(expression, expression.getText());
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