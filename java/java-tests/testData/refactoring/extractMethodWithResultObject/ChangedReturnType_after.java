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
//exit: CONTINUE PsiForeachStatement<-PsiContinueStatement
//exit: SEQUENTIAL PsiDeclarationStatement

    private String bar(Object o) {
    return "";
  }
}
