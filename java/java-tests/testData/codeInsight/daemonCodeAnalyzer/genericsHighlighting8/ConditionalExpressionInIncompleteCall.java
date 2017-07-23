
class Test{
  void test() {
    B b = new B<error descr="'B(java.lang.String, java.lang.String)' in 'Test.B' cannot be applied to '(java.lang.String)'">(true == false ? "bar" : null)</error>;
  }

  class B {
    B(String c, String d )  {}
  }
}
