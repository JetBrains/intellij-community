// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(.3F);
  }
  void foo(float f){}
}
