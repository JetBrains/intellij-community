// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(0.0F);
  }
  void foo(float f){}
}
