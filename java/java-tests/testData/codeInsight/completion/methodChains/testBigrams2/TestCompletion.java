import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */



class PsiElement {
  PsiFile getContainingFile() {
    return null;
  }
}

class PsiFile {
  VirtualFile getVirtualFile() {
    return null;
  }
}

class VirtualFile {}


public class TestCompletion {

  PsiElement e;

  public void method() {
    VirtualFile vf = <caret>
  }
}
