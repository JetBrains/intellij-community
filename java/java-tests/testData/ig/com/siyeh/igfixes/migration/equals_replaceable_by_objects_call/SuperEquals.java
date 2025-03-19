class T {
  String s;
  static class X extends T {
    boolean same(String s) {
      return super.s.<caret>equals(s);
    }
  }
}