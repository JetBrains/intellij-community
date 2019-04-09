class C {
  Object foo(boolean b) {
    if (b) {
        NewMethodResult x = newMethod();
        return x.returnResult;
    } else {
      return B.getInstance();
    }
  }

    NewMethodResult newMethod() {
        return new NewMethodResult(A.getInstance());
    }

    static class NewMethodResult {
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
}