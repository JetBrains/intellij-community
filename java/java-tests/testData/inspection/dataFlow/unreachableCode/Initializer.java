class Test {
  static {
    if (true) {
      throw new AssertionError();
    }
    <warning descr="Unreachable code">System.out.println("Unreachable");</warning>
  }
  
  static <warning descr="Unreachable code">{
    System.out.println("You cannot see me");
  }</warning>
}