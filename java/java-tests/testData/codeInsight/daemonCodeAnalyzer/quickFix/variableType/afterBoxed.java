// "Change parameter 'a' type to 'Long[]'" "true"

class Base {
  void m(Long[] a) {
    mg(a);
  }

  <T> void mg(T[] p) {}
}