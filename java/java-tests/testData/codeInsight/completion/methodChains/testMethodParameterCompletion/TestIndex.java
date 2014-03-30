/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiElement e) {
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
    e.getContainingClass();
  }
}

interface PsiElement {
  PsiClass getContainingClass();
}

interface PsiClass extends PsiElement {}
