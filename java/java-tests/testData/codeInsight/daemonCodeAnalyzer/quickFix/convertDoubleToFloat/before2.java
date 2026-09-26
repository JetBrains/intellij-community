// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(2<caret>.);
  }
  void foo(float f){}
}
