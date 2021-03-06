public class CountedLoopWithOverflow {
  void test() {
    for (int i = Integer.MAX_VALUE - 10; i != Integer.MIN_VALUE + 10; i++) {
      System.out.println(i);
      if (i == Integer.MIN_VALUE) {}
      if (i == Integer.MIN_VALUE + 9) {}
      if (<warning descr="Condition 'i == Integer.MIN_VALUE + 10' is always 'false'">i == Integer.MIN_VALUE + 10</warning>) {}
      if (<warning descr="Condition 'i == Integer.MIN_VALUE + 11' is always 'false'">i == Integer.MIN_VALUE + 11</warning>) {}
      if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {}
      if (<warning descr="Condition 'i == Integer.MAX_VALUE - 11' is always 'false'">i == Integer.MAX_VALUE - 11</warning>) {}
      if (i == Integer.MAX_VALUE - 10) {}
      if (i == Integer.MAX_VALUE - 9) {}
      if (i == Integer.MAX_VALUE) {}
    }

    for (long i = Long.MAX_VALUE - 10; i != Long.MIN_VALUE + 10; i++) {
      System.out.println(i);
      if (i == Long.MIN_VALUE) {}
      if (i == Long.MIN_VALUE + 9) {}
      if (<warning descr="Condition 'i == Long.MIN_VALUE + 10' is always 'false'">i == Long.MIN_VALUE + 10</warning>) {}
      if (<warning descr="Condition 'i == Long.MIN_VALUE + 11' is always 'false'">i == Long.MIN_VALUE + 11</warning>) {}
      if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {}
      if (<warning descr="Condition 'i == Long.MAX_VALUE - 11' is always 'false'">i == Long.MAX_VALUE - 11</warning>) {}
      if (i == Long.MAX_VALUE - 10) {}
      if (i == Long.MAX_VALUE - 9) {}
      if (i == Long.MAX_VALUE) {}
    }
  }
  
  void test2(int lo, int hi) {
    for(int i = lo; i != hi; i++) {
      if (i < lo) {
        System.out.println("possible");
      }
    }
    for(int i = lo; i < hi; i++) {
      if (<warning descr="Condition 'i < lo' is always 'false'">i < lo</warning>) {
        System.out.println("impossible");
      }
    }
  }
}