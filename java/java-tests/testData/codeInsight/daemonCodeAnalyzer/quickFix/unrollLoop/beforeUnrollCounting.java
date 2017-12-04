// "Unroll loop" "true"
class Test {
  void test() {
    fo<caret>r (int i = 0; i < 10; i++ // line comment
    ) {
      System.out.println("Hi!" + i);
    }
  }
}