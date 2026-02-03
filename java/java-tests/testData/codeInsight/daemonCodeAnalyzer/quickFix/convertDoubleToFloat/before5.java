// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(3<caret>.14);
  }
  void foo(float f){}
}
