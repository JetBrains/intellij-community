package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface ClassMemberWithElement extends ClassMember {
  PsiElement getElement();
}
