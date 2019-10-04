// "Change parameter 'a' type to 'Long[]'" "true"

class Base {
  void m(long[] a) {
    mg(<caret>a);
  }

  <T> void mg(T[] p) {}
}