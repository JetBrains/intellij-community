// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(1e-9F);
  }
  void foo(float f){}
}
