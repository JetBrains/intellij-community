public class TestIndex {

  public void m1(VirtualFile f) {
    PsiManager.getInstance().findFile(f).findElementAt(0);
    PsiManager.getInstance().findFile(f).findElementAt(0);
    PsiManager.getInstance().findFile(f).findElementAt(0);
    PsiManager.getInstance().findFile(f).findElementAt(0);
  }

  public void m2(UnknownParameter p) {
    SomeManager1.getInstance().findFile(p).findElementAt(0);
    SomeManager1.getInstance().findFile(p).findElementAt(0);
    SomeManager1.getInstance().findFile(p).findElementAt(0);
    SomeManager1.getInstance().findFile(p).findElementAt(0);

    SomeManager2.getInstance().findFile(p).findElementAt(0);
    SomeManager2.getInstance().findFile(p).findElementAt(0);
    SomeManager2.getInstance().findFile(p).findElementAt(0);
    SomeManager2.getInstance().findFile(p).findElementAt(0);

    SomeManager3.getInstance().findFile(p).findElementAt(0);
    SomeManager3.getInstance().findFile(p).findElementAt(0);
    SomeManager3.getInstance().findFile(p).findElementAt(0);
    SomeManager3.getInstance().findFile(p).findElementAt(0);

    SomeManager4.getInstance().findFile(p).findElementAt(0);
    SomeManager4.getInstance().findFile(p).findElementAt(0);
    SomeManager4.getInstance().findFile(p).findElementAt(0);
    SomeManager4.getInstance().findFile(p).findElementAt(0);
  }
}

interface PsiElement {

}

interface PsiFile {
  PsiElement findElementAt(int index);
}

interface VirtualFile {

}

class PsiManager {
  PsiFile findFile(VirtualFile vf) {
    return null;
  }

  public static PsiManager getInstance() {
    return null;
  }
}

interface UnknownParameter {
}

class SomeManager {
  PsiFile findFile(UnknownParameter parameter) {
    return null;
  }

  public static SomeManager getInstance() {
    return null;
  }
}

class SomeManager1 {
  PsiFile findFile(UnknownParameter parameter) {
    return null;
  }

  public static SomeManager1 getInstance() {
    return null;
  }
}

class SomeManager2 {
  PsiFile findFile(UnknownParameter parameter) {
    return null;
  }

  public static SomeManager2 getInstance() {
    return null;
  }
}

class SomeManager3 {
  PsiFile findFile(UnknownParameter parameter) {
    return null;
  }

  public static SomeManager3 getInstance() {
    return null;
  }
}

class SomeManager4 {
  PsiFile findFile(UnknownParameter parameter) {
    return null;
  }

  public static SomeManager4 getInstance() {
    return null;
  }
}