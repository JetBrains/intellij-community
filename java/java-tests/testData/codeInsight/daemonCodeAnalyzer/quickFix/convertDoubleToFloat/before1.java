// "Cast argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(1e<caret>1);
  }
  void foo(float f){}
}
