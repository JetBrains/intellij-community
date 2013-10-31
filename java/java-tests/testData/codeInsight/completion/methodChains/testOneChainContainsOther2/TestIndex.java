public class TestIndex {

  public void statMethod(PsiMethodCallExpression e) {
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
  }
}
interface PsiManager {

}
interface PsiElement {
  PsiManager getManager();
}
interface PsiClass extends PsiElement {
}
interface PsiMethod extends PsiElement {
  PsiClass getContainingClass();
}
interface PsiMethodCallExpression extends PsiElement {
  PsiMethod resolveMethod();
}
