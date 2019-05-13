/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {
  PsiElement e;

  public void statMethod() {
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
    e = PsiManager.getInstance(null).findFile(null).findElementAt(0);
  }
}

interface PsiFile extends PsiElement {
}

interface PsiElement {
  PsiElement findElementAt(int position);
}

class PsiManager {
  static PsiManager getInstance(Project p) {
    return null;
  }

  PsiFile findFile(VirtualFile vf) {
    return null;
  }
}

interface VirtualFile {}

interface Project {}
