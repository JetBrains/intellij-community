// "Move up into 'if' statement branches" "false"
class X {
  void test(int x) {
    if (x > 0) System.out.println(">0"); else if (x < 0) {}
    <selection>int y = x * 2;</selection>
    System.out.println(y);
  }
}