class T {
  static class A {
    String s;
  }
  A a;
  static boolean same(T t, String s) {
    return s.<caret>equals(t.a.s);
  }
}