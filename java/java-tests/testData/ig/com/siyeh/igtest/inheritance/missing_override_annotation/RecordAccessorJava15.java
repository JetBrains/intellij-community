record R(int x) {
  public int <warning descr="Missing '@Override' annotation on 'x()'">x</warning>() {return x;}
  public int y() {return x;}
}