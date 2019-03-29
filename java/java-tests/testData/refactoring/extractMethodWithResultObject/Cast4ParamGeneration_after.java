class Test {
  void foo(Object o) {
    if (o instanceof A) {
        NewMethodResult x = newMethod(o);
        ((A)o).bar();
    }
  }//ins and outs
//in: PsiParameter:o
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod(Object o) {
        ((A)o).bar();
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}

class A {
  void bar(){}
}