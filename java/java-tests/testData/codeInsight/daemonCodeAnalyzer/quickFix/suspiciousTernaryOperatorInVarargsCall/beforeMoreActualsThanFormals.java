// "Replace with 'new Object[]{a}'" "false"
// "Replace with 'new Object[]{b}'" "false"

class Test {
  static void bar(boolean flag) {
    Object[] a = {1, 2};
    Object b = "hello";
    foo(0, 1, flag ? a : b<caret>);
  }
  static void foo(int x, Object... xs) {
  }
}