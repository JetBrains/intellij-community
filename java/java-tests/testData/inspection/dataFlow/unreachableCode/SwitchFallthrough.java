class Test {
  void test(int x) {
    if (x < 0 || x > 2) return;
    switch(x) {
      default:
        <warning descr="Unreachable code">System.out.println("Impossible");</warning>
      case 0:
        System.out.println(0);
      case 1:
        System.out.println(1);
      case 2:
        System.out.println(2);
      case 3:
        System.out.println(3);
    }
  }
}