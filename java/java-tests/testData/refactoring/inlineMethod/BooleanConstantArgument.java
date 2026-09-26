interface PsiElement {
  PsiElement getNextSibling();
  PsiElement getPrevSibling();
}

class Test1 {
  private void foo(PsiElement node) {
    PsiElement element = getSibling(node, true);
  }

  private void bar(PsiElement node) {
    PsiElement element = getSibling(node, false);
  }

  private PsiElement getSibl<caret>ing(PsiElement element, boolean forward) {
    return forward ? element.getNextSibling() : element.getPrevSibling();
  }
}