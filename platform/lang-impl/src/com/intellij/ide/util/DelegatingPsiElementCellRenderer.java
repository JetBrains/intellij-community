// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiElement;

import javax.swing.*;

public class DelegatingPsiElementCellRenderer<T extends PsiElement> extends PsiElementListCellRenderer<T> {
  private final PsiElementCellRenderingInfo<T> myRenderingInfo;

  public DelegatingPsiElementCellRenderer(PsiElementCellRenderingInfo<T> info) {
    myRenderingInfo = info;
  }

  @Override
  protected int getIconFlags() {
    return myRenderingInfo.getIconFlags();
  }

  @Override
  public String getElementText(T element){
    return myRenderingInfo.getElementText(element);
  }

  @Override
  protected String getContainerText(T element, final String name){
    return myRenderingInfo.getContainerText(element, name);
  }

  @Override
  protected Icon getIcon(PsiElement element) {
    return myRenderingInfo.getIcon(element);
  }
}
