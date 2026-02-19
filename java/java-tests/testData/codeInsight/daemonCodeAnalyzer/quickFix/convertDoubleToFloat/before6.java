// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(1e-9<caret>d);
  }
  void foo(float f){}
}
