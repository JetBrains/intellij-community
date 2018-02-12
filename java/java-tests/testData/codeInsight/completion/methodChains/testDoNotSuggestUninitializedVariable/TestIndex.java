public class TestIndex {
  public void statMethod(PsiElement e) {
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
  }
}

interface Project {}

interface PsiElement {
  Project getProject();
}
