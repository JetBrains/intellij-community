// "Replace with 'new Object[]{b}'" "true"

class Test {
  static void bar(boolean flag) {
    Object[] a = {1, 2};
    Object b = "hello";
    foo(0, flag ? a : new Object[]{b});
  }
  static void foo(int x, Object... xs) {
  }
}