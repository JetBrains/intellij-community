// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;

public class MethodCellRenderer extends PsiElementListCellRenderer<PsiMethod>{
  private final boolean myShowMethodNames;
  private final PsiClassListCellRenderer myClassListCellRenderer = new PsiClassListCellRenderer();
  @PsiFormatUtil.FormatMethodOptions
  private final int myOptions;

  public MethodCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }
  public MethodCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    myShowMethodNames = showMethodNames;
    myOptions = options;
  }

  @Override
  public String getElementText(PsiMethod element) {
    final PsiNamedElement container = fetchContainer(element);
    String text = container instanceof PsiClass ? myClassListCellRenderer.getElementText((PsiClass)container) : container.getName();
    if (myShowMethodNames) {
      text += "."+PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY, myOptions, PsiFormatUtilBase.SHOW_TYPE);
    }
    return text;
  }

  @Override
  protected Icon getIcon(PsiElement element) {
    return super.getIcon(myShowMethodNames ? element : fetchContainer((PsiMethod)element));
  }

  private static PsiNamedElement fetchContainer(PsiMethod element){
    PsiClass aClass = element.getContainingClass();
    return aClass == null ? element.getContainingFile() : aClass;
  }

  @Override
  public String getContainerText(final PsiMethod element, final String name) {
    return PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  @Override
  public int getIconFlags() {
    return myClassListCellRenderer.getIconFlags();
  }
}
