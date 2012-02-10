// "Convert '.3' to float" "true"
class Test {
  void bar() {
    foo(.<caret>3);
  }
  void foo(float f){}
}
