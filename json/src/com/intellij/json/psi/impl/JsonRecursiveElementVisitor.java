package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;

/**
 * @author Mikhail Golubev
 */
public class JsonRecursiveElementVisitor extends JsonElementVisitor implements PsiRecursiveVisitor {

  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }
}
