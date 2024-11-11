class Test {
  void test() {
    var x = <error descr="Cannot resolve method 'unresolved' in 'Test'">unresolved</error>();
    System.out.println(x.hashCode());
    System.out.println(x.x);
  }
}