abstract class Test {
  void g() {
    new Object() {
      void foo() {
        Test.this.foo();
      }
    };
  }

  void foo() {
  }
}
class Test2 extends Test {

  {
    <caret>g();
  }
}