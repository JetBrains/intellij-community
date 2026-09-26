class ShiftOutOfRange {
  void test(int x, long y) {
    System.out.println(x <warning descr="Shift operation '<<' by negative constant value -1"><<</warning> -1);
    System.out.println(x << 0);
    System.out.println(x << 31);
    System.out.println(x <warning descr="Shift operation '<<' by overly large constant value 32"><<</warning> 32);
    System.out.println(y <warning descr="Shift operation '<<' by negative constant value -1"><<</warning> -1);
    System.out.println(y << 32);
    System.out.println(y << 63);
    System.out.println(y <warning descr="Shift operation '>>' by overly large constant value 64">>></warning> 64);
    System.out.println(y <warning descr="Shift operation '>>>' by overly large constant value 100">>>></warning> 100);
    System.out.println(y <warning descr="Shift operation '<<' by overly large constant value 1,000,000,000,000"><<</warning> 1_000_000_000_000L);
  }

  void dataflow(int x, long y) {
    if(y >= 32) {
      System.out.println(1 <warning descr="Shift operation '>>' by out-of-bounds value {32..Long.MAX_VALUE}">>></warning> y);
      System.out.println(1L >> y);
    }
    if (x < 0) {
      System.out.println(1 <warning descr="Shift operation '<<' by out-of-bounds value {Integer.MIN_VALUE..-1}"><<</warning> x);
      System.out.println(1 << y);
    }
    if(x == Long.SIZE) {
      System.out.println(y <warning descr="Shift operation '>>' by overly large constant value 64">>></warning> x);
    }
  }

}