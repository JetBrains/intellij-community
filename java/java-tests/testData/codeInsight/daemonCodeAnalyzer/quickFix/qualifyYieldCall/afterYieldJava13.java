// "Fix all 'Forward compatibility' problems in file" "true"
class Yield {
  class X {
    void test() {
      Yield.yield("x");
      Yield.this.yield(1);
    }
  }

  void test() {
    Yield.yield("x");
    this.yield(1);
  }

  void varYield() {
    int yield = 5;
    yield++;
    yield = 7;
  }

  void yield(int x) {}

  static void yield(String x) {
  }
}
