// "Create method 'fooBar'" "true"
interface I {
    static void fooBar() {
        
    }
}

class Test {
  void test() {
    Runnable runnable = I::fooBar;
  }
}
