public class TestIndex {

  Object o;

  public void statMethod(PsiClass c) {
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
    c.getMethod();
  }
}

interface PsiClass {
  PsiMethod getMethod();
}

interface PsiMethod {}
