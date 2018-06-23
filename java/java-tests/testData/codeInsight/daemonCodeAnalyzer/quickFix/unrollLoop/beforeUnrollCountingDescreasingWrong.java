// "Unroll loop" "false"
class Test {
  void test() {
    fo<caret>r (int i = 10; i <= 0; --i) {
      System.out.println("Hi!" + i);
    }
  }
}