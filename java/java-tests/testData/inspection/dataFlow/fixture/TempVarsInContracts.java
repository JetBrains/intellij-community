class X {
  native int getValue(int x);

  void test() {
    int result = Math.max(1, getValue(0));
    int result2 = Math.max(getValue(0), 1);
    if (<warning descr="Condition 'result > 0' is always 'true'">result > 0</warning>) {}
    if (<warning descr="Condition 'result2 > 0' is always 'true'">result2 > 0</warning>) {}
  }
}