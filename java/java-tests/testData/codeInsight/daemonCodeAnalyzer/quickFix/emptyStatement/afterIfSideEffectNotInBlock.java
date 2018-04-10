// "Extract side effects" "true"
class Test {
  void test(int x) {
    if(x > 0) {
        foo(x, 1);
        foo(x, 2);
    }
  }

  boolean foo(int x, int y) {
    System.out.println(x);
    return x > y;
  }
}