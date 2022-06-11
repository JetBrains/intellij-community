// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

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

  /**
   * @deprecated use {@link PsiClassRenderingInfo#getContainerTextStatic}
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public static String getContainerTextStatic(final PsiElement element) {
    return PsiClassRenderingInfo.getContainerTextStatic(element);
  }
}
