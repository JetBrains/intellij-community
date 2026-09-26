// "Introduce variable and replace with 's != null ?:'" "true-preview"
class A {
  int test(boolean b) {
      String s = b ? foo(1) : foo(2);
      return s != null ? s.length() : 0;
  }

  static String foo(int x) {
    return x > 0 ? "pos" : null;
  }
}
