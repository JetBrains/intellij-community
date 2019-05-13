public class TestIndex {

  public void statMethod(PsiMethod m) {
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
  }
}

interface PsiMember {
  PsiClass getContainingClass();
}

interface PsiMethod extends PsiMember {
}

interface PsiClass {}
