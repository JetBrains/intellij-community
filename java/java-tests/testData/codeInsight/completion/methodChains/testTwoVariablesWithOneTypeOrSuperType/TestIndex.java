/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiClass c, PsiMethod m) {
   c.getProject();
   c.getProject();
   c.getProject();
   c.getProject();
   m.getProject();
   m.getProject();
   m.getProject();
   m.getProject();
  }
}


class Project {
}

interface PsiElement {
  Project getProject();
}

class PsiClass implements PsiElement {
  public Project getProject() {
    return null;
  }
}

class PsiMethod implements PsiElement {
  public Project getProject() {
    return null;
  }
}
