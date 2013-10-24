public class TestIndex {
  public void m1() {
    //12
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
    PsiManager.getInstance();
  }

  public void m2(PsiClass psiClass) {
    //3
    psiClass.getManager();
    psiClass.getManager();
    psiClass.getManager();
  }

  public void m3(PsiMethod method) {
    //14
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
    method.getContainingClass();
  }

  public void m4(PsiMethodCallExpression psiMethodCallExpression) {
    //5
    psiMethodCallExpression.resolveMethod();
    psiMethodCallExpression.resolveMethod();
    psiMethodCallExpression.resolveMethod();
    psiMethodCallExpression.resolveMethod();
    psiMethodCallExpression.resolveMethod();
  }
}

class PsiManager {
  public static PsiManager getInstance() {
    return null;
  }
}

interface PsiClass {
  PsiManager getManager();
}

interface PsiMethod {
  PsiClass getContainingClass();
}

interface PsiMethodCallExpression {
  PsiMethod resolveMethod();
}