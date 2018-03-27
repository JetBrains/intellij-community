// "Change parameter 'a' type to 'T[]'" "false"

class Base {
  void m(long[] a) {
    mg(<caret>a);
  }

  <T> void mg(T[] p) {}
}