class Test {
  void foo() {
    new A() {
      void fe() {System.out.println("");}//ins and outs
//exit: SEQUENTIAL PsiMethod:fe

        NewMethodResult newMethod() {
            System.out.println("");
            return new NewMethodResult();
        }

        class NewMethodResult {
            public NewMethodResult() {
            }
        }
    };
  }
}
class A {
  void newMethod(){}
}