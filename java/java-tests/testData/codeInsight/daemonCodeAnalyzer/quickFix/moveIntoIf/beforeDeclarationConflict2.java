// "Move up into 'if' statement branches" "false"
class X {
  void test(int x) {
    if (x > 0) System.out.println(">0"); else {
      int y = 3;
    }
    <selection>{int y = x * 2;
    System.out.println(y);}</selection>
  }
}