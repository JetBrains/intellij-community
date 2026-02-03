class Test {
  void test() {
    foo("xyz");
    foo(<flown1111>123);
  }

  void foo(Object <flown111>obj) {
    boolean b = <flown1><flown11>obj instanceof String;
    if (<caret>b) {

    }
  }
}