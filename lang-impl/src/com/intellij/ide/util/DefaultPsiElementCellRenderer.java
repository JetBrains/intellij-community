package com.intellij.ide.util;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;

public class DefaultPsiElementCellRenderer extends PsiElementListCellRenderer {
  protected int getIconFlags() {
    return Iconable.ICON_FLAG_VISIBILITY;
  }

  public String getElementText(PsiElement element){
    return SymbolPresentationUtil.getSymbolPresentableText(element);
  }

  public String getContainerText(PsiElement element, final String name){
    return SymbolPresentationUtil.getSymbolContainerText(element);
  }

}