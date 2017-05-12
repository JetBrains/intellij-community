class Test {

  public static void test(int b) {
    do {
        if (b == 1) break;
        System.out.println();
    } while (<warning descr="Condition is always false">fa<caret>lse</warning>);
  }
}