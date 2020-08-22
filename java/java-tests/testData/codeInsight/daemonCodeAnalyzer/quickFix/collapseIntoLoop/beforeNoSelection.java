// "Collapse into loop" "true"
class X {
  void test() {
    <caret>foo("foo");
    foo("bar");
    foo(123);
  }
  
  void foo(Object obj) {}
}