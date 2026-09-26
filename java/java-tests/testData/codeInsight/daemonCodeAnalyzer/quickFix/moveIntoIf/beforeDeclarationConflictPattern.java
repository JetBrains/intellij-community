// "Move up into 'if' statement branches" "false"
class X {
  void test(int x, Object obj) {
    if (x > 0) System.out.println(">0"); else {
      int y = 3;
    }
    <selection>if (obj instanceof String y) {}</selection>
  }
}