import java.jang.String;


interface PsiElement {
  PsiClass getContainingClass();
}

interface PsiClass extends PsiElement {}

public class TestCompletion {

  public void method(PsiElement e) {
    int j = 1 + method123(<caret>);
  }

  public int method123(PsiClass c) {
    return 0;
  }
}
