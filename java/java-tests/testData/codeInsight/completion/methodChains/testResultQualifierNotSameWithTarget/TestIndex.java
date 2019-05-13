public class TestIndex {

  PsiFile f;

  void m() {
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
    f.findElementAt(0);
  }
}

interface PsiElement {
  PsiElement findElementAt(int offset);
}

interface PsiFile extends PsiElement {

}

