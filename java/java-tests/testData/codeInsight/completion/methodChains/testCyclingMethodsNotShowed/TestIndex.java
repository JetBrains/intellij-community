/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiElement e) {
    e.getParent();
    e.getParent();
    e.getParent();
    e.getParent();
    e.getPrevSibling();
    e.getPrevSibling();
    e.getPrevSibling();
    e.getPrevSibling();
  }
}

class PsiElement {
  public PsiElement getPrevSibling() {
    return null;
  }
  public PsiElement getParent() {
    return null;
  }
}
