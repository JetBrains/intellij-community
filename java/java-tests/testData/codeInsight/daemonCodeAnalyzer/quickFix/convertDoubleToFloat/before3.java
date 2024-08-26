// "Convert argument to 'float'" "true-preview"
class Test {
  void bar() {
    foo(.<caret>3);
  }
  void foo(float f){}
}
