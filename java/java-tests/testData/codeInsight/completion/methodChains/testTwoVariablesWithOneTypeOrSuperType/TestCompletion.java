/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

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


public class TestCompletion {
  PsiClass c = new PsiClass();

  public void method(PsiMethod m) {
    Project p = <caret>
  }
}
