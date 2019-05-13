// "Unroll loop" "false"
class Test {
  void test() {
    f<caret>or(int x : new int[]{1, 2, 3}) {
      System.out.println(x);
      x = 6;
      x++;
      System.out.println(x);
    }
  }

  void foo(boolean b) {}
}