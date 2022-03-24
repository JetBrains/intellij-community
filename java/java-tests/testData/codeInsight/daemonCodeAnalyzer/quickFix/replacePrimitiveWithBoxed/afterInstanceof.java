// "Replace 'int' with 'java.lang.Integer'" "true"
class Test {
  void foo(Object o) {
    boolean b = o instanceof Integer;
  }
}