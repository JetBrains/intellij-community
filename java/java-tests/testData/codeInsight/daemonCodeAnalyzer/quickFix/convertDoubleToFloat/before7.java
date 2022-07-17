// "Cast argument to 'float'" "true"
class Test {
  void bar() {
    foo(1e1<caret>37);
  }
  void foo(float f){}
}
