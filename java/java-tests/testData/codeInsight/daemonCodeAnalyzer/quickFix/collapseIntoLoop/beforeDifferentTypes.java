// "Collapse into loop" "false"
class X {
  void test() {
    <selection>foo("foo");
    foo("bar");
    foo(123);</selection>
  }
  
  void foo(Object obj) {}
}