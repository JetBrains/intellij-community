// "Create abstract method 'foo' in 'A'" "true"
abstract class A {
  void usage() {
    <caret>foo();
  }
}
