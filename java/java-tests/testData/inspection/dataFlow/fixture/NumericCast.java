class NumericCast {
  void test(int i) {
    if(i > 10 && i < 200) {
      byte b = (byte) i;
      if (<warning descr="Condition 'b == 0' is always 'false'">b == 0</warning>) {
        System.out.println("impossible");
      }
    }
    byte b = 100;
    b+=100;
    if (<warning descr="Condition 'b == -56' is always 'true'">b == -56</warning>) {
      System.out.println("always");
    }
  }

  void testMask(long x) {
    int bits = (int) ((x >> 16) & 0xFFFF);
    if (<warning descr="Condition 'bits >= 0' is always 'true'">bits >= 0</warning>) {
      System.out.println("Always");
    }
  }
}