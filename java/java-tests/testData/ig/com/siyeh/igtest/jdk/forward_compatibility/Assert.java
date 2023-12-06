class Test {
  void <warning descr="Use of 'assert' as an identifier is not supported in releases since Java 1.4">assert</warning>() {}
  
  void test() {
    int <warning descr="Use of 'assert' as an identifier is not supported in releases since Java 1.4">assert</warning> = 1;
    assert = 2;
    assert();
    new assert();
  }
  
  class <warning descr="Use of 'assert' as an identifier is not supported in releases since Java 1.4">assert</warning> {}
}