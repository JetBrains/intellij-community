// "Unwrap 'switch'" "true-preview"
class X {
  void test(int i) {
    int x;
      x = ((--i/*text*/) + "1" + (++i) + "1");
      System.out.println("1");
      ;
  }
}