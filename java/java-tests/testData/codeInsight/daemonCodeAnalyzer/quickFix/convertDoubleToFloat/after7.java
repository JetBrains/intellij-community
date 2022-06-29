// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo((float) 1e137);
  }
  void foo(float f){}
}
