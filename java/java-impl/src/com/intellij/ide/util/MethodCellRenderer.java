// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

public class MethodCellRenderer extends DelegatingPsiElementCellRenderer<PsiMethod> {

  public MethodCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }

  public MethodCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    super(new PsiMethodRenderingInfo(showMethodNames, options));
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
