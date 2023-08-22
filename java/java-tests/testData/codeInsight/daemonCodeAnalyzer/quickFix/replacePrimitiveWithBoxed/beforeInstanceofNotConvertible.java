// "Replace 'int' with 'java.lang.Integer'" "false"
class Test {
  void foo(String o) {
    boolean b = o instanceof int<caret>;
  }
}