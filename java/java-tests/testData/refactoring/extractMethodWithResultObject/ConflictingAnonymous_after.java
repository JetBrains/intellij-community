public class Test {
  void foo() {
    new A() {
      void fe() {System.out.println("");}//ins and outs
    }
  }
}
class A {
  void newMethod(){}
}