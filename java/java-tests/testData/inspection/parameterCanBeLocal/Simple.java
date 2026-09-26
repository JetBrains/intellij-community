class Temp {

  void test(int <warning descr="Parameter can be converted to a local variable">p</warning>) {
    p = 1;
    System.out.print(p);
  }
}