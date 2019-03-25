class A {
   int foo (Object o) {
     if (o == null) return 0;
     if (o == null) return 0;
     return 1;
   }//ins and outs
//in: PsiParameter:o
//exit: RETURN PsiMethod:foo<-PsiLiteralExpression:0
//exit: SEQUENTIAL PsiIfStatement
//exit count: 2
}