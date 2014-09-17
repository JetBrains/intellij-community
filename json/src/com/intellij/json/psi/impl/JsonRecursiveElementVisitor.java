package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.psi.PsiElement;

/**
 * @author Mikhail Golubev
 */
public class JsonRecursiveElementVisitor extends JsonElementVisitor {

  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }
}
