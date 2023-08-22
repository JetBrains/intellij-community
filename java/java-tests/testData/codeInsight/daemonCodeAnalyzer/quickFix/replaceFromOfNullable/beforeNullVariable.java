// "Replace with '.empty()'" "true-preview"
class A {
  void test() {
    String s = null;
    java.util.Optional.ofNullable(<caret>s);
  }
}