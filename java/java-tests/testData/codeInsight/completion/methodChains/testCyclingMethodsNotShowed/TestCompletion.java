/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class PsiElement {
  public PsiElement getPrevSibling() {
    return null;
  }
  public PsiElement getParent() {
    return null;
  }
}

public class TestCompletion {
  public void method(PsiElement e) {
    PsiElement anotherElement = <caret>
  }
}
