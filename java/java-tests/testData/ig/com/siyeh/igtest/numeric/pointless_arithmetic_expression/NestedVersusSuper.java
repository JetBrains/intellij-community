class Test {

  int i = 0;

  void test() {
    class Inner extends Test {
      Inner() {
        Test.this.i = 10;
      }

      int t() {
        return Test.this.i - this.i; // different fields
      }
    }
    System.out.println(new Inner().t());
  }

  public static void main(String[] args) {
    Test one = new Test();
    one.test();
  }

}
