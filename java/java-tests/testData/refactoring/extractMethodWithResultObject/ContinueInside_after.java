class Test {
  String foo(String[] args) {

    for(String arg : args) {
      if (arg == null) continue;
      System.out.println(arg);
    }
    if (args.length == 0) return null;

    return null;
  }//ins and outs
//in: PsiParameter:args
//out: INSIDE PsiLiteralExpression:null
//out: OUTSIDE null
}
