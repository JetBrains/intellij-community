class Test {

  public static void test() {
    while ((<warning descr="Condition is always false">fa<caret>lse</warning>)) {
        <error descr="Unreachable statement">System.out.println();</error>
    }
  }
}