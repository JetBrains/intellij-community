class X {
  void test(int a, int b, int c){
    int x;
    if((a > 0? c < b && (x = b) > 0: (x = c) < 0 || c == b)) {
      System.out.println(x);
    } else {
      System.out.println(<error descr="Variable 'x' might not have been initialized">x</error>);
    }
  }
}