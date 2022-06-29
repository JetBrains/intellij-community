// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(1e-9F);
  }
  void foo(float f){}
}
