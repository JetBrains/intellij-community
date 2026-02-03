/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */


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

public class TestCompletion {
  public void method(PsiMethod m) {
    Project project = <caret>
  }
}
