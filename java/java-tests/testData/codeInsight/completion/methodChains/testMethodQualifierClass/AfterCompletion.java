interface PsiMember {
  PsiClass getContainingClass();
}

interface PsiMethod extends PsiMember {
}

interface PsiClass {}

public class TestCompletion {
  public void method() {
      PsiMember psiMember = null;
      PsiClass c = psiMember.getContainingClass()
  }
}
