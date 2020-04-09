public class Test {
  void foo() {
    new A() {
      void fe() {
          Test.this.newMethod();
      }
    }
  }

    private void newMethod() {
        System.out.println("");
    }
}
class A {
  void newMethod(){}
}