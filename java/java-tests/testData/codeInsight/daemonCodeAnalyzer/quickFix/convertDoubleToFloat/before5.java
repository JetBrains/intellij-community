// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(3<caret>.14);
  }
  void foo(float f){}
}
