public class Test {
  void foo() {
    new A() {
      void fe() {System.out.println("");}//ins and outs
//exit: SEQUENTIAL PsiMethod:fe
    }
  }
}
class A {
  void newMethod(){}
}