public class TestIndex {

  public void statMethod(PsiManager m, PsiElement e) {
    m.getProject();
    m.getProject();
    m.getProject();
    m.getProject();
    m.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
  }
}

interface PsiManager {
  Project getProject();
}

interface PsiElement {
  Project getProject();
}

interface Project {}