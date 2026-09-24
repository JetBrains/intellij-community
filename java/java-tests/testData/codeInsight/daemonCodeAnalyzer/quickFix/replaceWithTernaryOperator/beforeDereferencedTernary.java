// "Introduce variable and replace with 's != null ?:'" "true-preview"
class A {
  int test(boolean b) {
    return (b ? foo(1) : foo(2)).len<caret>gth();
  }

  static String foo(int x) {
    return x > 0 ? "pos" : null;
  }
}
