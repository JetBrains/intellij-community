// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(2.F);
  }
  void foo(float f){}
}
