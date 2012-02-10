// "Convert '1e137' to float" "false"
class Test {
  void bar() {
    foo(1e1<caret>37);
  }
  void foo(float f){}
}
