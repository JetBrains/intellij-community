// "Extract side effect" "true-preview"
class Test {
  void test(int x) {
      foo(x, 2);
  }

  boolean foo(int x, int y) {
    System.out.println(x);
    return x > y;
  }
}