
class Test{
  void test() {
    B b = new B<error descr="Expected 2 arguments but found 1">(true == false ? "bar" : null)</error>;
  }

  class B {
    B(String c, String d )  {}
  }
}
