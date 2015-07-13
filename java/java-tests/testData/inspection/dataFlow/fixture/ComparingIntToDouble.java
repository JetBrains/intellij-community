class Test {
  public static void testFunc() {
    int i = 10;
    double d = 10;
    if (<warning descr="Condition 'i == d' is always 'true'">i == d</warning>) {
      System.out.println();
    }
  }
}