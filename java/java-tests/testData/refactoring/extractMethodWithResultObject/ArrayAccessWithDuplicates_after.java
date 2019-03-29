class Test {
  void foo(String[] ss) {
      NewMethodResult x = newMethod(ss);
      System.out.println(ss[0]);
    System.out.println(ss[0]);
  }//ins and outs
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiExpressionStatement

    NewMethodResult newMethod(String[] ss) {
        System.out.println(ss[0]);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}