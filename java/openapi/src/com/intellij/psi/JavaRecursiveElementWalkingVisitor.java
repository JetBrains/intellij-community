/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.jsp.JspFile;

public abstract class JavaRecursiveElementWalkingVisitor extends JavaElementVisitor {
  private final PsiWalkingState myWalkingState = new PsiWalkingState(this){
    public void elementFinished(PsiElement element) {
      JavaRecursiveElementWalkingVisitor.this.elementFinished(element);
    }
  };

  @Override
  public void visitElement(PsiElement element) {
    myWalkingState.elementStarted(element);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  protected void elementFinished(PsiElement element) {
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitExpression(expression);
    myWalkingState.startedWalking(); // do not traverse from scratch
    visitReferenceElement(expression);
  }

  //override in order to visit each root directly in visitor
  @Override public void visitJspFile(JspFile file) {
    super.visitJspFile(file);
    visitClass(file.getJavaClass());
    visitFile(file.getBaseLanguageRoot());
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
