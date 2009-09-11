package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author cdr
 */
public abstract class PsiWalkingState extends WalkingState<PsiElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiWalkingState");
  private final PsiElementVisitor myVisitor;

  private static class PsiTreeGuide implements TreeGuide<PsiElement> {
    public PsiElement getNextSibling(PsiElement element) {
      return element.getNextSibling();
    }

    public PsiElement getPrevSibling(PsiElement element) {
      return element.getPrevSibling();
    }

    public PsiElement getFirstChild(PsiElement element) {
      return element.getFirstChild();
    }

    public PsiElement getParent(PsiElement element) {
      return element.getParent();
    }

    private static final PsiTreeGuide instance = new PsiTreeGuide();
  }

  protected PsiWalkingState(PsiElementVisitor delegate) {
    super(PsiTreeGuide.instance);
    myVisitor = delegate;
  }

  @Override
  public void visit(PsiElement element) {
    element.accept(myVisitor);
  }

  @Override
  public void elementStarted(PsiElement element) {
    if (!startedWalking && element instanceof PsiCompiledElement) {
      // do not walk inside compiled PSI since getNextSibling() is too slow there
      LOG.error(element+"; Do not use walking visitor inside compiled PSI since getNextSibling() is too slow there");
    }

    super.elementStarted(element);
  }
}
