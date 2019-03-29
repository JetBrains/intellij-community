class Test {
  void foo(String[] ss, String[] bb) {
     for(int i = 0; i < ss.length; i++) {

         NewMethodResult x = newMethod(ss, i, bb);
         System.out.println(ss[i]);
       System.out.println(bb[i]);

     }
  }//ins and outs
//in: PsiLocalVariable:i
//in: PsiParameter:bb
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod(String[] ss, int i, String[] bb) {
        System.out.println(ss[i]);
        System.out.println(bb[i]);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}