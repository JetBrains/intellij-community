import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */


class ElementFactory {
  PsiClassType createType(PsiClass c, Object obj) {
    return null;
  }

  PsiClassType createType(PsiClass c) {
    return null;
  }
}

class PsiClassType {}

class PsiClass {}

public class TestCompletion {

  public void method(ElementFactory f) {
    PsiClassType t = <caret>
  }
}
