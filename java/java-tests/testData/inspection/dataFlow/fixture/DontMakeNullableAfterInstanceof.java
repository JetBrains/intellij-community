class Some {
  void foo(Object o) {
    if (o instanceof String) {

    }
    o.hashCode();
  }

  private void getOwnerClass(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass && hashCode() == 42) {
        return;
      }
      element = element.getParent();
    }
  }

}


interface PsiElement {
  PsiElement getParent();
}
interface PsiClass extends PsiElement {}