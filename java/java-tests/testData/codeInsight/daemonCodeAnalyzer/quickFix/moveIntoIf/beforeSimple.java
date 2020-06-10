// "Move up into 'if' statement branches" "true"
class X {
  void test(int x) {
    if (x > 0) {}
    <caret>System.out.println(x);
  }
}