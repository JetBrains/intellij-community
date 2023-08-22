// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;

public class PsiClassListCellRenderer extends DelegatingPsiElementCellRenderer<PsiClass> {

  public PsiClassListCellRenderer() {
    super(PsiClassRenderingInfo.INSTANCE);
  }

  // For binary compatibility
  @Override
  public String getElementText(PsiClass element) {
    return super.getElementText(element);
  }

  // For binary compatibility
  @Override
  protected String getContainerText(PsiClass element, String name) {
    return super.getContainerText(element, name);
  }
}
