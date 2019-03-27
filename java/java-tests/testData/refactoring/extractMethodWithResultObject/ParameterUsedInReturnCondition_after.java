class C {
  String[] array;

  boolean test(String a, String b) {
    for (String s : array) {
      if (a.equals(s)) {

        if (b == null) {
          return true;
        }
        if (b.equals(s)) {
          return true;
        }

      }
    }
    return false;
  }//ins and outs
//in: PsiParameter:b
//in: PsiParameter:s
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:true
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:true
//exit: SEQUENTIAL PsiForeachStatement
//exit count: 2
}