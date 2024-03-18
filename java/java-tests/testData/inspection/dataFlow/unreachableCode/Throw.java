class Test {
  int test(int x) {
    if (x < 0) {
      System.exit(0);
      throw new AssertionError("unreachable");
    }
    System.out.println(x);
    return 5;
  }
}