public class Test {
  void foo(Object o) {
    if (o instanceof A) {
      ((A)o).bar();
    }
  }//ins and outs
//in: PsiParameter:o
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod(Object o) {
        ((A)o).bar();
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }
}

class A {
  void bar(){}
}