// "Replace 'int' with 'java.lang.Integer'" "true-preview"
class Test {
  void foo(Object o) {
    boolean b = o instanceof Integer;
  }
}