public class Test {
  void foo() {
    new A() {
      void fe() {<selection>System.out.println("");</selection>}
    }
  }
}
class A {
  void newMethod(){}
}