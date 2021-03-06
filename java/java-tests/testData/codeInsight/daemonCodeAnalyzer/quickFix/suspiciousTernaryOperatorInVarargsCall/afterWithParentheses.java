// "Replace with 'new Object[]{b}'" "true"

class Test {
  static void bar(boolean flag) {
    Integer[] a = {1, 2};
    Integer b = 42;
    foo(0, (((flag ? a : new Object[]{b}))));
  }
  static void foo(int x, Object... xs) {
  }

  public static void main(String[] args) {
    bar(true);
  }
}