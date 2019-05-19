// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;

public class MethodOrFunctionalExpressionCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  private final PsiClassListCellRenderer myClassListCellRenderer = new PsiClassListCellRenderer();
  private final MethodCellRenderer myMethodCellRenderer;

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }
  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    myMethodCellRenderer = new MethodCellRenderer(showMethodNames, options);
  }

  @Override
  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getElementText((PsiMethod)element)
                                        : ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)element, false);
  }

  @Override
  protected Icon getIcon(PsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getIcon(element) : super.getIcon(element);
  }

  @Override
  public String getContainerText(final NavigatablePsiElement element, final String name) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getContainerText((PsiMethod)element, name)
                                        : PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  @Override
  public int getIconFlags() {
    return myClassListCellRenderer.getIconFlags();
  }
}
