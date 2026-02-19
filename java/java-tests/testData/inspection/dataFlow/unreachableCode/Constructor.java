class Test {
  Test() {
    if (true) {
      throw new AssertionError();
    }
    <warning descr="Unreachable code">System.out.println("Hello");</warning>
  }
}