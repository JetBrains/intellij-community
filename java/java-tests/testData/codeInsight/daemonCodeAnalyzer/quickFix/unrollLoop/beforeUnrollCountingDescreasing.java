// "Unroll loop" "true-preview"
class Test {
  void test() {
    fo<caret>r (int i = 10; 0 <= i; --i) {
      System.out.println("Hi!" + i);
    }
  }
}