class Temp {
  public Temp(int <warning descr="Parameter can be converted to a local variable">p</warning>) {
    for (int i = 0; i < 10; i++) {
      p = i;
      System.out.print(p);
    }
  }
}