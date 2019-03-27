class C {
  String test(int n) {
    String s = "";

    if (n == 1) {
      return "A" + s;
    }
    if (n == 2) {
      return "B" + s;
    }

    return null;
  }//ins and outs
//in: PsiLocalVariable:s
//in: PsiParameter:n
//exit: RETURN PsiMethod:test<-PsiBinaryExpression:"A" + s
//exit: RETURN PsiMethod:test<-PsiBinaryExpression:"B" + s
//exit: SEQUENTIAL PsiIfStatement
//exit count: 2
}