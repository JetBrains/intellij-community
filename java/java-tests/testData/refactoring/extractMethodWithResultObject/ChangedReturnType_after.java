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
//out: OUTSIDE null

    private String bar(Object o) {
    return "";
  }
}
