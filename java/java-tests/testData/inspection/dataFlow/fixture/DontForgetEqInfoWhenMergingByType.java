class Some {

  private boolean canBePatternVariable(PsiElement element) {
    if (element instanceof LeafElement) {
      return true;
    }

    while (!(element instanceof LeafElement) && element != null) {
      element = getNextObject(element);
    }
    return element != null;
  }

  PsiElement getNextObject(PsiElement element) { return element; }
  
  class LeafElement {}
  interface PsiElement {}

}


