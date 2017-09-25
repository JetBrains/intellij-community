// "Unroll loop" "true"
class Test {
  void test() {
      foo(true);
      unresolved(!true);
      foo(false);
      unresolved(!false);
  }

  void foo(boolean b) {}
}