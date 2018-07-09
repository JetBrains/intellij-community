// "Change parameter 'a' type to 'java.lang.Long[]'" "true"

class Base {
  void m(long[] a) {
    mg(<caret>a);
  }

  <T> void mg(T[] p) {}
}