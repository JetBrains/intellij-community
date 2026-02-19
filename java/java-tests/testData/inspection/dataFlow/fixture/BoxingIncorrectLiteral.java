class Testcase {
  void test() {
    Character x = <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Character'">"foo";</error>
  }
}