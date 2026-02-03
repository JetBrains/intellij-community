class A {
  private static void m(int a) { }
  public static void m(String s) { }
}

class B {
  void m() {
    int xxx = 0;
    String xxy = "";
    A.m(xxy);<caret>
  }
}