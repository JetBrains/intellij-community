import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class Some {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper();
    h.getTag();
  }
}

class Some1 {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper();
    h.getTag();
  }
}

class Some2 {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper() ;
    h.getTag();
  }
}

class Some3 {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper() ;
    h.getTag();
  }
}

class Some4 {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper();
    h.getTag();
  }
}

class Some5 {
  public void m() {
    MethodJavaDocHelper h = new MethodJavaDocHelper()  ;
    h.getTag();
  }
}

interface PsiElement {

}

class MethodJavaDocHelper {
  public PsiElement getTag() {
    return null;
  }
}

public class TestCompletion {

  public void method() {
    PsiElement e = <caret>
  }
}
