class Test {
  public int fib(int n) {
    switch (n) {
    case 0:
      return 0;
    // you can comment out the next three lines -- same thing
    case 1:
    case 2:
      return 1;
    default:
      int a;
      int b = 1;
      int c = 2;
      n -= 3;
      while (n-- > 0) {
        a = b;
        b = c;
        c = a + b;
      }
      return c;
    }
  }
}

