class Test {
  void test(Object object) {
    if (object instanceof String <warning descr="The value of pattern variable 's' is never used">s</warning>) {
      s = "hello"; // not reported
      System.out.println(s);
    }
    if (object instanceof String ignored) {
    }

    if (object instanceof R(var x, var <warning descr="The value of pattern variable 'y' is never used">y</warning>)) {
      if (1 == 1) {
        System.out.println(x);
      }
      x = 2;
      System.out.println(x);
      y = 1;
      System.out.println(y);
    }
  }

  record R(int x, int y) {

  }
}