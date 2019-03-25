class C {
  Object foo(boolean b) {
    if (b) {
      return A.getInstance();
    } else {
      return B.getInstance();
    }
  }//ins and outs
//exit: RETURN PsiMethod:foo<-PsiMethodCallExpression:A.getInstance()

    public NewMethodResult newMethod() {
        return A.getInstance(); // todo
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
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