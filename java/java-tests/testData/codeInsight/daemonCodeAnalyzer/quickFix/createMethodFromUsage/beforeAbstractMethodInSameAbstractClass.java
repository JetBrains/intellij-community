// "Create abstract method 'foo' in 'A'" "true-preview"
abstract class A {
  void usage() {
    <caret>foo();
  }
}
