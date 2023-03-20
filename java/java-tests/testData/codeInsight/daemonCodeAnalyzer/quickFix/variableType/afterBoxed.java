// "Change parameter 'a' type to 'Long[]'" "true-preview"

class Base {
  void m(Long[] a) {
    mg(a);
  }

  <T> void mg(T[] p) {}
}