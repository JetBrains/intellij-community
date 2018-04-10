// "Unroll loop" "true"
class Test {
  void test() {
    fo<caret>r (long i = 0; i <= 10; i++) {
      if (i % 7 == 6) break;
      System.out.println("Hi!" + i);
    }
  }
}