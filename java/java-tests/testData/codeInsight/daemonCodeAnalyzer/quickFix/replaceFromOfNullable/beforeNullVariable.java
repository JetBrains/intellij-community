// "Replace with '.empty()'" "true"
class A {
  void test() {
    String s = null;
    java.util.Optional.ofNullable(<caret>s);
  }
}