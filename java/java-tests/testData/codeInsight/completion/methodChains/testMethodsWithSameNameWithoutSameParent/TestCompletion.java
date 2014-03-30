interface PsiManager {
  Project getProject();
}

interface PsiElement {
  Project getProject();
}

interface Project {}


public class TestCompletion {

  public void method(PsiElement e, PsiManager m) {
    Project p = <caret>
  }
}