// "Unroll loop" "true-preview"
class X {
  void test() {
    <caret>for (int x : new int[]{1}) {
      class Y {
        int a = x;
      }
    }
    class Y {}
  }
}