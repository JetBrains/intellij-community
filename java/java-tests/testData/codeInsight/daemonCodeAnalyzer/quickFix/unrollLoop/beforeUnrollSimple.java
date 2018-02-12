// "Unroll loop" "true"
class Test {
  void test() {
    fo<caret>r(int i : new int[] {1,2,3,4}) {
      System.out.println(i);
    }
  }
}