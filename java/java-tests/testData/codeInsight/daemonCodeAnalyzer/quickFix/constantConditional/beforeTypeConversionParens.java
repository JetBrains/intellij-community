// "Simplify" "true-preview"
class Test {
  void test() {
    Object s = <caret>true ? "foo" + "bar" : 1;
  }
}