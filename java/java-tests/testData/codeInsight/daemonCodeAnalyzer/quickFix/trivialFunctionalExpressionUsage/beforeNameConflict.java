// "Replace call with method body" "true"

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