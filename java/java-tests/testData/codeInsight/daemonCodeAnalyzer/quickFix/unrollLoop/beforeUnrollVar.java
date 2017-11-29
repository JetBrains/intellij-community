// "Unroll loop" "true"
class Test {
  void test() {
    boolean steps = {true, false};
    fo<caret>r(boolean step : steps) {
      foo(step);
      unresolved(!step);
    }
  }

  void foo(boolean b) {}
}