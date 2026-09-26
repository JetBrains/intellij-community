class Test {
  void <warning descr="Use of 'enum' as an identifier is not supported in releases since Java 1.5">enum</warning>() {}
  
  void test() {
    int <warning descr="Use of 'enum' as an identifier is not supported in releases since Java 1.5">enum</warning> = 1;
    enum = 2;
    enum();
    new enum();
  }
  
  class <warning descr="Use of 'enum' as an identifier is not supported in releases since Java 1.5">enum</warning> {}
}