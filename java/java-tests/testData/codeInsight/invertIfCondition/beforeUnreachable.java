// "Invert 'if' condition" "true"
class A {
  void foo(boolean b, String[] entries) {
    {
      try {
        String filePath = "";
        for (String entry : entries) {
          final String rootPath = "";
          i<caret>f (b) {
            break;
          }
        }
      }
      finally {
      }
    }
  }
}