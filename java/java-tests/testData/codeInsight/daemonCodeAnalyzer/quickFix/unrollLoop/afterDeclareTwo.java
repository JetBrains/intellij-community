// "Unroll loop" "true-preview"
class X {
  void test() {
      {
          int y = 1 + 1;
          System.out.println(y);
      }
      {
          int y = 2 + 1;
          System.out.println(y);
      }
  }
}