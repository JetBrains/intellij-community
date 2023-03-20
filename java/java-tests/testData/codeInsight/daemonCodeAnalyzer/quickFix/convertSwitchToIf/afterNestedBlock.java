// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int x) {
      if (x == 1) {
          {
              System.out.println("hello");
          }
      } else if (x == 2) {
          System.out.println("oops");
      }
  }
}