/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;



public abstract class PsiRecursiveElementVisitor extends PsiElementVisitor {
  public void visitElement(PsiElement element) {
    element.acceptChildren(this);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }
}
