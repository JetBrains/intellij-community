public class Test {
  void foo() {
    new A() {
      void fe() {System.out.println("");}//ins and outs
//exit: SEQUENTIAL PsiMethod:fe

        public NewMethodResult newMethod() {
            System.out.println("");
            return new NewMethodResult();
        }

        public class NewMethodResult {
            public NewMethodResult() {
            }
        }
    }
  }
}
class A {
  void newMethod(){}
}