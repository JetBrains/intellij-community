// "Replace call with method body" "true-preview"

class Test {
  void test() {
    new Runnable() {
      @Override
      public void run() {
        int a =6;
      }
    }.r<caret>un();
    int a =5;
  }
}