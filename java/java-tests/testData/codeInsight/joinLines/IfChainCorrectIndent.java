class Foo {
  void test(int a, int b) {
<caret>    if (a > 0) {
      if (b > 0) {
        System.out.println("A");
        System.out.println("B");
        System.out.println("C");
          System.out.println("Deeper");
   System.out.println("Wrong indent");
      }
    }
  }
}