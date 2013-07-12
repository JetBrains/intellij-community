/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiElementFactory f) {
    f.createClass();
    f.createClass();
    f.createClass();
    f.createClass();
    f.createClass();
    f.createClass();
  }
}

class PsiElement {
}

class PsiClass extends PsiElement {
}

class PsiElementFactory {
  public PsiClass createClass() {
    return null;
  }
}
