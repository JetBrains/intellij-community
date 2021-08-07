// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MethodOrFunctionalExpressionCellRenderer extends DelegatingPsiElementCellRenderer<NavigatablePsiElement> {
  public static class MethodOrFunctionalExpressionCellRenderingInfo
    implements PsiElementRenderingInfo<NavigatablePsiElement> {
    private final PsiMethodRenderingInfo myMethodCellRenderer;

    public MethodOrFunctionalExpressionCellRenderingInfo(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
      myMethodCellRenderer = new PsiMethodRenderingInfo(showMethodNames, options);
    }

    @Override
    public @NotNull String getPresentableText(@NotNull NavigatablePsiElement element) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getPresentableText((PsiMethod)element)
                                          : ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)element, false);
    }

    @Override
    public @Nullable String getContainerText(@NotNull NavigatablePsiElement element) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getContainerText((PsiMethod)element)
                                          : PsiClassRenderingInfo.getContainerTextStatic(element);
    }

    @Override
    public @Nullable Icon getIcon(@NotNull NavigatablePsiElement element) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getIcon((PsiMethod)element) : PsiElementRenderingInfo.super.getIcon(element);
    }
  }

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    super(new MethodOrFunctionalExpressionCellRenderingInfo(showMethodNames, options));
  }
}
