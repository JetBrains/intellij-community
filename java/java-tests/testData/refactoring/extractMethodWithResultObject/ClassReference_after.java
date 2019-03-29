class C {
  Object foo(boolean b) {
    if (b) {
        NewMethodResult x = newMethod();
        return A.getInstance();
    } else {
      return B.getInstance();
    }
  }//ins and outs
//exit: RETURN PsiMethod:foo<-PsiMethodCallExpression:A.getInstance()

    NewMethodResult newMethod() {
        return new NewMethodResult(A.getInstance());
    }

    class NewMethodResult {
        private Object returnResult;

        public NewMethodResult(Object returnResult) {
            this.returnResult = returnResult;
        }
    }
}
class A {
  static A getInstance() {
    return new A();
  }
}
class B extends A {
  static B getInstance() {
    return new B();
  }
}