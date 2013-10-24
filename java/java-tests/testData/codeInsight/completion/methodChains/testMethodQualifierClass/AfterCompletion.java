interface PsiMember {
  PsiClass getContainingClass();
}

interface PsiMethod extends PsiMember {
}

interface PsiClass {}

public class TestCompletion {
  public void method() {
      PsiMethod psiMethod = <caret><selection>null</selection>;
      PsiClass c = psiMethod.getContainingClass()
  }
}
