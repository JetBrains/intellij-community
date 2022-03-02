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

      int t1() {
        return <warning descr="Result of 'this.i - this.i' is always '0'">this.i - this.i</warning>;
      }

      int t2() {
        return <warning descr="Result of 'Test.this.i - Test.this.i' is always '0'">Test.this.i - Test.this.i</warning>;
      }
    }
    System.out.println(new Inner().t());
  }

  public static void main(String[] args) {
    Test one = new Test();
    one.test();
  }

}
