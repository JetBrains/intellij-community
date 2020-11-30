// "Fix all 'Suspicious ternary operator in varargs method call' problems in file" "false"

class Test {
  static void bar(boolean flag) {
    Object[] a = {1, 2};
    Object b = "hello";
    foo(0, 1, flag ? a : b);
  }
  static void foo(int x, Object... xs) {
  }
}