// "Convert '1e-9d' to float" "false"
class Test {
  void bar() {
    foo(1e-9<caret>d);
  }
  void foo(float f){}
}
