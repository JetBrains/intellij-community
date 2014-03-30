/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiElement e) {
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
    e.getContainingFile().getVirtualFile();
  }
}

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
