// "Move up into 'if' statement branches" "true"
class X {
  void test(int x) {
    if (x > 0) {
        System.out.println(">0");
        int y = x * 2;
        System.out.println(y);
    } else {
        if (x < 0) {}
        int y = x * 2;
        System.out.println(y);
    }
  }
}