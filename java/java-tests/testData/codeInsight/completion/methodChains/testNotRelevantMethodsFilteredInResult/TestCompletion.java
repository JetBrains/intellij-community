public class TestCompletion {

  void m() {
    PsiFile f = <caret>
  }
}

interface PsiFile {
}

interface PsiElement {
  PsiFile getContainingFile();
}

class SomeUtil {
  //representate some not frequently used methods
  public static PsiFile get1() {
    return null;
  }
  public static PsiFile get2() {
    return null;
  }
  public static PsiFile get3() {
    return null;
  }
  public static PsiFile get4() {
    return null;
  }
  public static PsiFile get5() {
    return null;
  }
}
