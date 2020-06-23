class Foo {
  void test() {
    new Object() {
      void foo(String s) {
        System.out.println(s);
      }
    }.f<caret>oo("hello");
  }
}