package com.intellij.ide.impl;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class PsiElementArrayDataValidator extends DataValidator.ArrayValidator<PsiElement> {
  public PsiElementArrayDataValidator() {
    super(new PsiElementDataValidator());
  }
}