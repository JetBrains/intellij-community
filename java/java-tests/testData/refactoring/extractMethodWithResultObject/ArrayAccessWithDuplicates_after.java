class Test {
  void foo(String[] ss) {
    System.out.println(ss[0]);
    System.out.println(ss[0]);
  }//ins and outs
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiExpressionStatement

    NewMethodResult newMethod(String[] ss) {
        System.out.println(ss[0]);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}