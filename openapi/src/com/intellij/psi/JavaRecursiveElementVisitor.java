/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.jsp.JspFile;
import com.intellij.util.containers.Stack;

public abstract class JavaRecursiveElementVisitor extends JavaElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private final Stack<PsiReferenceExpression> myRefExprsInVisit = new Stack<PsiReferenceExpression>();
  private final Stack<PsiBinaryExpression> myBinaryExpressions = new Stack<PsiBinaryExpression>();

  @Override
  public void visitElement(PsiElement element) {
    if (!myRefExprsInVisit.isEmpty() && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
    }
    else if (element instanceof PsiBinaryExpression) {
      //implement smart traversing to avoid stack overflow
      if (!myBinaryExpressions.isEmpty() && myBinaryExpressions.peek() == element) {
        return;
      }
      PsiElement child = element.getFirstChild();
      while (child != null) {
        if (child instanceof PsiBinaryExpression) {
          myBinaryExpressions.push((PsiBinaryExpression)child);
        }
        child.accept(this);
        child = child.getNextSibling();
        if (child == null) {
          child = myBinaryExpressions.isEmpty() ? null : myBinaryExpressions.pop();
          if (child != null) child = child.getFirstChild();
        }
      }
    }
    else {
      element.acceptChildren(this);
    }
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    myRefExprsInVisit.push(expression);
    try {
      visitExpression(expression);
      visitReferenceElement(expression);
    }
    finally {
      myRefExprsInVisit.pop();
    }
  }

  //override in order to visit each root directly in visitor
  @Override public void visitJspFile(JspFile file) {
    super.visitJspFile(file);
    visitClass(file.getJavaClass());
    visitFile(file.getBaseLanguageRoot());
  }
}
