// "Unroll loop" "true-preview"
class X {
  void test() {
    <caret>for (int x : new int[]{1}) {
      int y = x + 1;
      System.out.println(y);
    }
    int y = 2;
  }
}