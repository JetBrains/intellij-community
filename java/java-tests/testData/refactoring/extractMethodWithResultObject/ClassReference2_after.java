class C {
  Object foo(boolean b) {
    if (b) {
      return A.getInstance();
    } else {
      return B.getInstance();
    }
  }//ins and outs
//out: INSIDE PsiMethodCallExpression:A.getInstance()
}
class A {
  static A getInstance() {
    return new A();
  }
}
class B extends A {
}