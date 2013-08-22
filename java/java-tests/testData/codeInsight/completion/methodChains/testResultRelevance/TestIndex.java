/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  PsiElement e;

  void m() {
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
  }
}

class JavaPsiFacade {
  static JavaPsiFacade getInstance() {
    return null;
  }

  PsiClass findClass() {
    return null;
  }
}

interface PsiElement {
  PsiClass getContainingClass();
}

interface PsiClass extends PsiElement {
}
