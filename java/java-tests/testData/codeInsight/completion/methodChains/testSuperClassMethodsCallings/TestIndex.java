/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiElement a, PsiElement b) {
    a.getProject();
    a.getProject();
    a.getProject();
    a.getProject();
    b.getProject();
    b.getProject();
    b.getProject();
    b.getProject();
  }
}


class Project {
}

interface PsiElement {
  Project getProject();
}

class PsiMethod implements PsiElement {
  public Project getProject() {
    return null;
  }
}
