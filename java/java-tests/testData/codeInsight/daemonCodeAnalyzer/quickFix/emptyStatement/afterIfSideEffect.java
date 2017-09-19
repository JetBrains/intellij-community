// "Extract side effects as an 'if' statement" "true"
class Test {
  void test(int x) {
      if (x > 0) {
          foo(x, 1);
          foo(x, 2);
      } else {
          foo(x, 3);
      }
  }

  boolean foo(int x, int y) {
    System.out.println(x);
    return x > y;
  }
}