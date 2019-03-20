class Test {
  void foo(String[] ss, String[] bb) {
     for(int i = 0; i < ss.length; i++) {

       System.out.println(ss[i]);
       System.out.println(bb[i]);

     }
  }//ins and outs
//in: PsiLocalVariable:i
//in: PsiParameter:bb
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiMethod:foo
}