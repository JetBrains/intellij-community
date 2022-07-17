// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(1e1F);
  }
  void foo(float f){}
}
