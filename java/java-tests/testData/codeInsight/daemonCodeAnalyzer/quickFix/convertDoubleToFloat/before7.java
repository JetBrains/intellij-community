// "Cast argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(1e1<caret>37);
  }
  void foo(float f){}
}
