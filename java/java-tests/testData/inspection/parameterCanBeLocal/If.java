class Temp {
  public boolean flag;

  void test(int <warning descr="Parameter can be converted to a local variable">p</warning>) {
    if (flag) {
      p = 1;
    }
    else {
      p = 2;
    }
    System.out.print(p);
  }
}