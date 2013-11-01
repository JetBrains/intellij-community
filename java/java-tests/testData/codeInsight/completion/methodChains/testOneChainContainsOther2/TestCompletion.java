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
public class TestCompletion {

  public void method() {
    PsiManager m = <caret>
  }
}
