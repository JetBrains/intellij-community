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

public class TestCompletion {

  public void method(VirtualFile f) {
    PsiElement element = <caret>
  }
}
