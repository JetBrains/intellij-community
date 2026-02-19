// "Replace with 'new Object[]{b}'" "false"

class Test {
  static void bar(boolean flag) {
    int[] a = {1, 2};
    Integer b = 42;
    foo(0, flag ? a : b<caret>);
  }
  static void foo(int x, Object... xs) {
  }
}