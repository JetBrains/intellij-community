// "Create inner class 'Generic'" "true-preview"
class Base {
  void foo(){}
}
class Test extends Base {
  Test() {
    super.foo(new Generic<String> ());
  }

    private class Generic<T> {
    }
}