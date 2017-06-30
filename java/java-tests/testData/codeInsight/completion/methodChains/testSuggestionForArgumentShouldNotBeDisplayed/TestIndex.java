public class TestIndex {

  public void statMethod(PsiMethod m) {
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
  }
}

class PsiClass {

}

class PsiMethod {
  public PsiClass getContainingClass() {
     return null;
  }
}
