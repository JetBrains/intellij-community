class Test {
  void foo(String... strs){}
  void bar() {
    foo(new S<caret>tring[] {
      "edwqefwe", //my comment
    });
  }
}