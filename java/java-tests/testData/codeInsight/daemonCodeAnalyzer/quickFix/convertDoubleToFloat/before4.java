// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(0<caret>.0);
  }
  void foo(float f){}
}
