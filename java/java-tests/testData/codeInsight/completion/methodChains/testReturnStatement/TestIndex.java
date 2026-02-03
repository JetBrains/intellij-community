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

class PsiClass {
}

class PsiElementFactory {
  public PsiClass createClass() {
    return null;
  }
}