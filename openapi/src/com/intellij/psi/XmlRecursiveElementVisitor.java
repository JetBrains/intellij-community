/*
 * @author max
 */
package com.intellij.psi;

public class XmlRecursiveElementVisitor extends XmlElementVisitor {
  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }
}