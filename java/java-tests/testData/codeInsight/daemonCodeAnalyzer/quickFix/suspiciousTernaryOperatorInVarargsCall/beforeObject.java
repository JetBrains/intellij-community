// "Replace with 'new Object[]{b}'" "true"

class Test {
  public static void main(String[] args) {
    Object[] a = {1, 2};
    Object b = "hello";
    foo(0, a);
    foo(0, b);
    for (boolean flag : new boolean[]{true, false}) {
      foo(0, flag ? a : b<caret>);
      foo(0, 1, flag ? a : b);
    }
  }
  static void foo(int x, Object... xs) {
  }
}