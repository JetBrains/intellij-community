// "Create Method 'fooBar'" "true"
interface I {
}

class Test {
  void test() {
    Runnable runnable = I::foo<caret>Bar;
  }
}
