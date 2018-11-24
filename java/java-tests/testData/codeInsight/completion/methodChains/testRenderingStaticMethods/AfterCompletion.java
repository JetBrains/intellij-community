import java.jang.String;

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

  void mm();
}

interface PsiClass extends PsiElement {
}

public class TestCompletion {

  public void method(PsiElement e) {
    PsiClass c = JavaPsiFacade.getInstance().findClass()
  }

}
