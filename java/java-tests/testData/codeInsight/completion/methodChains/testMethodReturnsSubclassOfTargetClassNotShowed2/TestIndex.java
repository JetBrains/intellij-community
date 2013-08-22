/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiClass c) {
   c.findMethodByName("asd");
   c.findMethodByName("asd");
   c.findMethodByName("asd");
   c.findMethodByName("asd");
  }
}

class PsiMethod implements PsiElement {
}

interface PsiElement {
}

class PsiClass {
  public PsiMethod findMethodByName(String methodName) {
    return null;
  }
}
