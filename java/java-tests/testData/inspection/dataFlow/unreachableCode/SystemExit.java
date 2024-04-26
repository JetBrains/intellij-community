class Test {
  void test() {
    System.exit(0);
    <warning descr="Unreachable code">System.out.println("unreachable");</warning>
  }
}