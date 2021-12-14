class Test {

  public static void test() {
    while (<error descr="Loop condition is always false making the loop body unreachable">(false)</error>) {
        System.out.println();
    }
    boolean c = false;
    while ((<warning descr="Condition 'c' is always 'false'"><caret>c</warning>)) {
        System.out.println();
    }
  }
}