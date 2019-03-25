public class Test {
  void foo() {
    new A() {
      void fe() {System.out.println("");}//ins and outs
//exit: SEQUENTIAL PsiMethod:fe

        public NewMethodResult newMethod() {
            return new NewMethodResult();
        }

        public class NewMethodResult {
        }
    }
  }
}
class A {
  void newMethod(){}
}