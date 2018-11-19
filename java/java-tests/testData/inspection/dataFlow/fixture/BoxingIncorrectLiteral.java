class Testcase {
  void test() {
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Character'">Character x = "foo";</error>
  }
}