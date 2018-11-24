class PsiClass {

}

class PsiMethod {
  public PsiClass getContainingClass() {
    return null;
  }
}

public class TestCompletion {
  public void method(PsiMethod m) {
    anotherMethod(xxx.<caret>);
  }

  void anotherMethod(PsiClass psiClass) {

  }
}
