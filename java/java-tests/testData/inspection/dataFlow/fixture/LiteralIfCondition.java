class Test {

  public static void test() {
    if (<warning descr="Condition is always false">fa<caret>lse</warning>) {
      System.out.println();
    }
  }
}