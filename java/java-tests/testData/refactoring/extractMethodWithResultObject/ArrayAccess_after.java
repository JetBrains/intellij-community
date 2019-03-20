class Test {
  void foo(String[] ss) {
     for(int i = 0; i < ss.length; i++) {
       System.out.println(ss[i]);
       System.out.println(ss[i] + ss[i]);
     }
  }//ins and outs
//in: PsiLocalVariable:i
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiMethod:foo
}