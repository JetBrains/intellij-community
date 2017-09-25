// "Extract side effects as an 'if' statement" "true"
class Test {
  void test(int x) {
    i<caret>f(x > 0 ? !foo(x, 1) ^ foo(x, 2) : foo(x, 3)) {

    }
  }

  boolean foo(int x, int y) {
    System.out.println(x);
    return x > y;
  }
}