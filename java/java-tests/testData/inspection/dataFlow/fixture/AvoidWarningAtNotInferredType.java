class Test {
  void test() {
    var x = <error descr="Cannot resolve method 'unresolved' in 'Test'">unresolved</error>();
    System.out.println(x.<error descr="Cannot resolve method 'hashCode()'">hashCode</error>());
    System.out.println(x.<error descr="Cannot resolve symbol 'x'">x</error>);
  }
}