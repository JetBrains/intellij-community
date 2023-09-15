// "Unroll loop" "true-preview"
class X {
  void test() {
    <caret>for (int x : new int[]{1, 2}) {
      int y = x + 1;
      System.out.println(y);
    }
  }
}