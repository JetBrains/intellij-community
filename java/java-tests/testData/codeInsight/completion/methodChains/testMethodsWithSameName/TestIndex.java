import java.lang.Object;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(ElementFactory ef) {
    ef.createType(null);
    ef.createType(null);
    ef.createType(null);
    ef.createType(null);
    ef.createType(null, null);
    ef.createType(null, null);
    ef.createType(null, null);
    ef.createType(null, null);
  }
}

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
