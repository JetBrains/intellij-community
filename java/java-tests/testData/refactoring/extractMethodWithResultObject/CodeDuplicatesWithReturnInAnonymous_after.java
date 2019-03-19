class Test10 {
  void test() {
    new Object() {
      int get() {
        return 0;
      }
    };

    new Object() {
      int get() {
        return 0;
      }
    };
  }//ins and outs
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:0
}