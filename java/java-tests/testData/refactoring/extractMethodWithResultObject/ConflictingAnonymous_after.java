class Test {
  void foo() {
    new A() {
      void fe() {
          NewMethodResult x = newMethod();
      }//ins and outs
//exit: SEQUENTIAL PsiMethod:fe

        NewMethodResult newMethod() {
            System.out.println("");
            return new NewMethodResult();
        }
    };
  }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
class A {
  void newMethod(){}
}