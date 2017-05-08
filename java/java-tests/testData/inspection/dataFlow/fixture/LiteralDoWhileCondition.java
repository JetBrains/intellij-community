class Test {

  public static void test() {
    do {
        System.out.println();
    } while (<warning descr="Condition is always false">fa<caret>lse</warning>);
  }
}