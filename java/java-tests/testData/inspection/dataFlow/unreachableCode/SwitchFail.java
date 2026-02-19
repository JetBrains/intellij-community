class Test {
  void test(int x) {
    switch(x) {
      default:
        System.exit(0);
        <warning descr="Unreachable code">System.out.println("oops");
        return;</warning>
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