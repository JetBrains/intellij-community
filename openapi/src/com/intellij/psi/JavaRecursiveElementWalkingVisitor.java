/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.jsp.JspFile;
import com.intellij.util.containers.Stack;

public abstract class JavaRecursiveElementWalkingVisitor extends JavaElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private Stack<PsiReferenceExpression> myRefExprsInVisit;

  private boolean startedWalking;
  private boolean isDown;

  @Override
  public void visitElement(PsiElement element) {
    if (myRefExprsInVisit != null && !myRefExprsInVisit.isEmpty() && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
      return;
    }
    isDown = true;
    if (!startedWalking) {
      startedWalking = true;
      walk(element);
    }
  }

  private void walk(PsiElement root) {
    for (PsiElement element = next(root, root); element != null; element = next(element, root)) {
      isDown = false; // if client visitor did not call default visitElement it means skip subtree
      PsiElement parent = element.getParent();
      PsiElement next = element.getNextSibling();
      element.accept(this);
      assert element.getNextSibling() == next;
      assert element.getParent() == parent;
    }
    startedWalking = false;
  }

  private PsiElement next(PsiElement element, PsiElement root) {
    if (isDown) {
      PsiElement child = element.getFirstChild();
      if (child != null) return child;
    }
    else {
      isDown = true;
    }
    // up
    while (element != root) {
      PsiElement next = element.getNextSibling();
      if (next != null) return next;
      element = element.getParent();
    }
    return null;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    if (myRefExprsInVisit == null) myRefExprsInVisit = new Stack<PsiReferenceExpression>();
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