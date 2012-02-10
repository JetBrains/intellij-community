// "Convert '2.' to float" "true"
class Test {
  void bar() {
    foo(2<caret>.);
  }
  void foo(float f){}
}
