// "Move up into 'if' statement branches" "false"
class Test {
  void test(int x) {
    if (x > 0) return;
    <caret>System.out.println(x);
  }
}