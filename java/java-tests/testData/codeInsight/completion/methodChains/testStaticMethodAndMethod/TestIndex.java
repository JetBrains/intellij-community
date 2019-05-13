/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod() {
    JavaPsiFacade.findClass("asd");
    JavaPsiFacade.findClass("asd");
    JavaPsiFacade.findClass("asd");
  }

  public void statMethod(PsiMethod m) {
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
    m.getContainingClass();
  }
}

class JavaPsiFacade {
  public static PsiClass findClass(String qName) {
    return null;
  }
}

class PsiClass {

}

class PsiMethod {
  public PsiClass getContainingClass() {
     return null;
  }
}
