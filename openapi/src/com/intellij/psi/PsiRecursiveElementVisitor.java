/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import java.util.Stack;


public abstract class PsiRecursiveElementVisitor extends PsiElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private Stack<PsiReferenceExpression> myRefExprsInVisit = new Stack<PsiReferenceExpression>();

  public void visitElement(PsiElement element) {
    if (myRefExprsInVisit.size() > 0 && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
    }
    else {
      element.acceptChildren(this);
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    myRefExprsInVisit.push(expression);
    try {
      super.visitReferenceExpression(expression);
    }
    finally {
      myRefExprsInVisit.pop();
    }
  }
}
