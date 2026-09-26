import java.io.IOException;

class Test {
  void foo(Exception targetElement) {
    PsiPackage aPack = newMethod((IOException) targetElement);
  }

    private PsiPackage newMethod(IOException targetElement) {
        return JavaDirectoryService.getInstance().getPackage(targetElement);
    }
}