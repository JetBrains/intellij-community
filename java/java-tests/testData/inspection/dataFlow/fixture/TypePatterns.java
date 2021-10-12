class Test {
  static void test1(Object o) {
    final int a;
    switch (o) {
      case String s: {
        a = -1;
        break;
      }
      case default: {
        a = 2;
        break;
      }
    }
    if (a < 0) {
      System.out.println(a);
    }
  }
}