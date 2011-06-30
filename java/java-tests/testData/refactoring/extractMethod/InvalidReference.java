import java.io.IOException;

class Test {
  void foo(Exception targetElement) {
    PsiPackage aPack = <selection>JavaDirectoryService.getInstance().getPackage((IOException)targetElement)</selection>;
  }
}