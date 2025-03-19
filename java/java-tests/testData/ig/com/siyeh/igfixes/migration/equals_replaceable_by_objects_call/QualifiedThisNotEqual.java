class T {
  String s;
  class A {
    boolean notSame(String s) {
      return T.this.s != s && (T.this.s == null || !T.this.s.<caret>equals(s));
    }
  }
}