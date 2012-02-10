// "Convert '3.14' to float" "true"
class Test {
  void bar() {
    foo(3<caret>.14);
  }
  void foo(float f){}
}
