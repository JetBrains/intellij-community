public class TestIndex {

  void m(PsiElement e) {
    //10
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
    e.getContainingFile();
  }

  void m2() {
    SomeUtil.get1();
    SomeUtil.get2();
    SomeUtil.get3();
    SomeUtil.get4();
    SomeUtil.get5();
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
