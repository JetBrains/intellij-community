interface PsiMember {
  PsiClass getContainingClass();
}

interface PsiMethod extends PsiMember {
}

interface PsiClass {}

public class TestCompletion {
  public void method() {
      PsiMethod psiMethod = null;
      PsiClass c = psiMethod.getContainingClass()
  }
}
