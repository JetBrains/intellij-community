import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class PsiElement {
}

class PsiClass extends PsiElement {
}

class PsiElementFactory {
  public PsiClass createClass() {
    return null;
  }
}

public class TestCompletion {

  public void method(PsiElementFactory f) {
    PsiElement e = <caret>
  }
}
