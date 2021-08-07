class Foo {
  void test(boolean a, boolean b) {
    <caret>if(a &&) {
      if (b) {
        System.out.println("x");
      }
    }
  }
}