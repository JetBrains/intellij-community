class C {
  String test() {
    while (true) {
      try {
        return get();
      }
      catch (Exception e) {}

      if (foo()) {
        return null;
      }
      if (bar()) {
        return null;
      }

    }
  }//ins and outs
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:null
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:null
//exit: SEQUENTIAL PsiBlockStatement
//exit count: 2

    String get() { return null; }
  boolean foo() { return false; }
  boolean bar() { return false; }
}