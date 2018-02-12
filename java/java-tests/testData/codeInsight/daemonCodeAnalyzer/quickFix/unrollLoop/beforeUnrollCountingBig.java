// "Unroll loop" "false"
class Test {
  void test() {
    fo<caret>r (int i = 0; i < 1000; i++) {
      System.out.println("Hi!" + i);
    }
  }
}