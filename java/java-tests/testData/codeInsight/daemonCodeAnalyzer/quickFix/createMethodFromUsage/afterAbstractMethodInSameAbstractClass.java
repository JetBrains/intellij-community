// "Create abstract method 'foo' in 'A'" "true-preview"
abstract class A {
  void usage() {
    foo();
  }

    protected abstract void foo();
}
