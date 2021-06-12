// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MethodCellRenderer extends DelegatingPsiElementCellRenderer<PsiMethod> {
  public static class MethodCellRenderingInfo implements PsiElementCellRenderingInfo<PsiMethod> {
    private final boolean myShowMethodNames;
    @PsiFormatUtil.FormatMethodOptions
    private final int myOptions;

    public MethodCellRenderingInfo(boolean showMethodNames) {
      this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
    }
    public MethodCellRenderingInfo(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
      myShowMethodNames = showMethodNames;
      myOptions = options;
    }

    @Override
    public Icon getIcon(PsiElement element) {
      return PsiElementCellRenderingInfo.super.getIcon(myShowMethodNames ? element : fetchContainer((PsiMethod)element));
    }

    @Override
    public int getIconFlags() {
      return PsiClassListCellRenderer.INFO.getIconFlags();
    }

    @Override
    public String getElementText(PsiMethod element) {
      final PsiNamedElement container = fetchContainer(element);
      String text = container instanceof PsiClass ? PsiClassListCellRenderer.INFO.getElementText((PsiClass)container) : container.getName();
      if (myShowMethodNames) {
        text += "."+PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY, myOptions, PsiFormatUtilBase.SHOW_TYPE);
      }
      return text;
    }

    @Override
    public String getContainerText(PsiMethod element, String name) {
      return PsiClassListCellRenderer.getContainerTextStatic(element);
    }
  }

  public MethodCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }

  public MethodCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    super(new MethodCellRenderingInfo(showMethodNames, options));
  }

  private static PsiNamedElement fetchContainer(@NotNull PsiMethod element){
    PsiClass aClass = element.getContainingClass();
    return aClass == null ? element.getContainingFile() : aClass;
  }

  // For binary compatibility
  @Override
  public String getContainerText(final PsiMethod element, final String name) {
    return super.getContainerText(element, name);
  }

  // For binary compatibility
  @Override
  public String getElementText(PsiMethod element) {
    return super.getElementText(element);
  }

  // For binary compatibility
  @Override
  public int getIconFlags() {
    return super.getIconFlags();
  }
}
