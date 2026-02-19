class Test {
  public static boolean test(String s) {
    boolean b;
    b &= false;
    <caret>if (s == null) b &= true;
  }
}