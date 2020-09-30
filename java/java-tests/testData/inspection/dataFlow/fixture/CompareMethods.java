class CompareMethods {
  void testBoolean() {
    if(<warning descr="Condition 'Boolean.compare(Boolean.TRUE, Boolean.FALSE) == 1' is always 'true'">Boolean.compare(Boolean.TRUE, Boolean.FALSE) == 1</warning>) {}
  }
  void testConstInt() {
    int x = 2;
    int y = 3;
    int z = Integer.compare(x, y);
    if (<warning descr="Condition 'z > 0' is always 'false'">z > 0</warning>) {}
    long l1 = Long.MIN_VALUE;
    long l2 = Long.MAX_VALUE;
    if (<warning descr="Condition 'Long.compare(l1, l2) == Long.compareUnsigned(l1, l2)' is always 'false'">Long.compare(l1, l2) == Long.compareUnsigned(l1, l2)</warning>) {}
    double d1 = Double.NaN;
    double d2 = Double.NaN;
    if (<warning descr="Condition 'Double.compare(d1, d2) == 0' is always 'true'">Double.compare(d1, d2) == 0</warning>) {}
  }
  
  void testRelative(int x, int y, int[] data) {
    if (x < y) {
      int res = Integer.compare(x, y);
      if (<warning descr="Condition 'res >= 0' is always 'false'">res >= 0</warning>) {}
    }
    if (x != y) {
      int res = Long.compare(x, y);
      if (res == 0) {}
    }
    int r = Integer.compare(data.length, 0);
    if (r > 0) {}
    if (r == 0) {}
    if (<warning descr="Condition 'r < 0' is always 'false'">r < 0</warning>) {}
  }
}
