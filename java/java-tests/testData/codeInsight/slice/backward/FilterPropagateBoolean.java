class Test {
  void test() {
    foo(<flown1111>"xyz");
    foo(123);
  }

  void foo(Object <flown111>obj) {
    boolean b = <flown1><flown11>obj instanceof String;
    if (<caret>b) {

    }
  }
}