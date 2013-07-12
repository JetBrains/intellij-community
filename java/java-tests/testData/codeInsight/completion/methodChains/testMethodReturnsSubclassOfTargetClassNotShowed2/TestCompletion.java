/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */


class PsiMethod implements PsiElement {
}

interface PsiElement {
}

class PsiClass {
  public PsiMethod findMethodByName(String methodName) {
    return null;
  }
}

public class TestCompletion {

  PsiClass c;

  public void method() {
    PsiElement element = <caret>
  }
}
