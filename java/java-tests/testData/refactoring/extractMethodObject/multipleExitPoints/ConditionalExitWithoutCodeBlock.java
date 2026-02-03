class Test {
  void foo(String[] args){
    for (String aArg : args) {
      <selection>boolean a = aArg == null;
      if (aArg == null) continue;</selection>

      System.out.println(aArg + a);
    }
  }
}
