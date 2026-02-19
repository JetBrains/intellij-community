
record Test2(int x, int y) {
  static void test(Test2 test2) {
    if (test2.x() == test2.y()) {
      if (<warning descr="Condition 'test2.x == test2.y' is always 'true'">test2.x == test2.y</warning>) {

      }
    }

  }

  public int x() {
    return x;
  }
}