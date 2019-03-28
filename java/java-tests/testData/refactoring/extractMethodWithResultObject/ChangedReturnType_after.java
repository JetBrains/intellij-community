class X {
  void foo(java.util.List l) {
    for (Object o : l) {
      if (o == null) continue;
      String x = bar(o);
      System.out.println(x);
    }
  }//ins and outs
//in: PsiParameter:o
//out: PsiLocalVariable:x
//exit: CONTINUE PsiBlockStatement<-PsiContinueStatement
//exit: SEQUENTIAL PsiDeclarationStatement

    public NewMethodResult newMethod(Object o) {
        if (o == null) return new NewMethodResult((1 /* exit key */), x); //todo
        String x = bar(o);
        return new NewMethodResult((-1 /* exit key */), x);
    }

    public class NewMethodResult {
        private int exitKey;
        private String x;

        public NewMethodResult(int exitKey, String x) {
            this.exitKey = exitKey;
            this.x = x;
        }
    }

    private String bar(Object o) {
    return "";
  }
}
