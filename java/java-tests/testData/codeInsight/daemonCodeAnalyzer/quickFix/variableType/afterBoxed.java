// "Change parameter 'a' type to 'java.lang.Long[]'" "true"

class Base {
  void m(Long[] a) {
    mg(a);
  }

  <T> void mg(T[] p) {}
}