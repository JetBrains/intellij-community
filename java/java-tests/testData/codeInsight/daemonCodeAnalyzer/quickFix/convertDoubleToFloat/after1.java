// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(1e1F);
  }
  void foo(float f){}
}
