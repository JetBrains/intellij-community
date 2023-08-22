// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiElement;

import javax.swing.*;

public class DelegatingPsiElementCellRenderer<T extends PsiElement> extends PsiElementListCellRenderer<T> {

  private final PsiElementRenderingInfo<? super T> myRenderingInfo;

  public DelegatingPsiElementCellRenderer(PsiElementRenderingInfo<? super T> info) {
    myRenderingInfo = info;
  }

  @Override
  public String getElementText(T element) {
    return myRenderingInfo.getPresentableText(element);
  }

  @Override
  protected String getContainerText(T element, final String name) {
    return myRenderingInfo.getContainerText(element);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Icon getIcon(PsiElement element) {
    return myRenderingInfo.getIcon((T)element);
  }
}
