// "Create inner class 'Generic'" "true-preview"
class Base {
  void foo(){}
}
class Test extends Base {
  Test() {
    super.foo(new <caret>Generic<String> ());
  }
}