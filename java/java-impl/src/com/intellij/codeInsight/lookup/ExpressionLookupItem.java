/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.*;
import com.intellij.util.PlatformIcons;

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
      setIcon(PlatformIcons.METHOD_ICON);
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