// "Unroll loop" "true-preview"
class X {
  void test() {
    <caret>for (int x : new int[]{1}) {
      int y = x + 1;
      class Y{}
      System.out.println(y);
    }
  }
}