import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */



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


public class TestCompletion {

  public void method() {
    PsiElement e = <caret>
  }
}
