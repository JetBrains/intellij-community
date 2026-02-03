class Foo {
  void test(int a, int b) {
    if (a > 0 &<caret>& b > 0) {
      System.out.println("A");
      System.out.println("B");
      System.out.println("C");
        System.out.println("Deeper");
   System.out.println("Wrong indent");
    }
  }
}