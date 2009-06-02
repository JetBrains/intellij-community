package com.intellij.psi;

/**
 * @author cdr
 */
public abstract class PsiWalkingState {
  private boolean isDown;
  private boolean startedWalking;
  private final PsiElementVisitor myVisitor;

  public abstract void elementFinished(PsiElement element);

  protected PsiWalkingState(PsiElementVisitor delegate) {
    myVisitor = delegate;
  }

  public void elementStarted(PsiElement element){
    isDown = true;
    if (!startedWalking) {
      startedWalking = true;
      walkChildren(element);
      startedWalking = false;
    }
  }

  private void walkChildren(PsiElement root) {
    for (PsiElement element = next(root,root,isDown); element != null; element = next(element, root, isDown)) {
      isDown = false; // if client visitor did not call default visitElement it means skip subtree
      PsiElement parent = element.getParent();
      PsiElement next = element.getNextSibling();
      element.accept(myVisitor);
      assert element.getNextSibling() == next;
      assert element.getParent() == parent;
    }
  }

  private PsiElement next(PsiElement element, PsiElement root, boolean isDown) {
    if (isDown) {
      PsiElement child = element.getFirstChild();
      if (child != null) return child;
    }
    // up
    while (element != root) {
      PsiElement next = element.getNextSibling();

      elementFinished(element);
      if (next != null) {
        assert next.getPrevSibling() == element : "Element: "+element+"; next.prev: "+next.getPrevSibling()+"; File: "+element.getContainingFile();
        return next;
      }
      element = element.getParent();
    }
    elementFinished(element);
    return null;
  }

  public void startedWalking() {
    startedWalking = true;
  }
}
