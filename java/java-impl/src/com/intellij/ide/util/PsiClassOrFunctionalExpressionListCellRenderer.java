// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

public class PsiClassOrFunctionalExpressionListCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  @Override
  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiClass ? ClassPresentationUtil.getNameForClass((PsiClass)element, false)
                                       : ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)element, false);
  }

  @Override
  protected String getContainerText(NavigatablePsiElement element, final String name) {
    return PsiClassRenderingInfo.getContainerTextStatic(element);
  }
}
