class Mapping {
  private int myInt;

  public void <caret>method(boolean b, int i, char c, double d, int[] ia, String s) {
    myInt = b ? i + c - (int)d: ia.length + s.hashCode();
  }

  public void context() {
    myInt = true || false ? 5 + 'z' - (int)3.14 : new int[] { 0, 1 }.length + "abc".hashCode();
  }
}
