// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(1e-9<caret>d);
  }
  void foo(float f){}
}
