interface PsiMember {
  PsiClass getContainingClass();
}

interface PsiMethod extends PsiMember {
}

interface PsiClass {}

public class TestCompletion {
  public void method() {
    PsiClass c = <caret>
  }
}
