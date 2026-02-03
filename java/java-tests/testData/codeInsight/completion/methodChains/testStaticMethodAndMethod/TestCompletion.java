/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

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

public class TestCompletion {
  public void method(PsiMethod m) {
    PsiClass someClass = <caret>
  }
}
