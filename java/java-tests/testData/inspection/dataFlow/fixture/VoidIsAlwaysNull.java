class VoidIsAlwaysNull {
  private void test2(Class<?> cls) {
    if (Void.class.isAssignableFrom(cls)) {}
    if (cls.isAssignableFrom(Void.class)) {}
  }

  void testVoidClass() {
    test2(Void.class);
  }
  
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