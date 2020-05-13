class VoidIsAlwaysNull {
  // IDEA-195506
  void foo(Void p) {
    System.out.println(p.<warning descr="Method invocation 'toString' will produce 'NullPointerException'">toString</warning>());
  }
  
  Void noCastReport() {
    return (Void)null;
  }
  
  Void noCastReport2() {
    Object var = null;
    return (Void)var;
  }
}