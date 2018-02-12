class PsiClass {
}

class PsiElementFactory {
  public PsiClass createClass() {
    return null;
  }
}

public class TestCompletion {

  public PsiClass method(PsiElementFactory f) {
    return <caret>
  }
}
