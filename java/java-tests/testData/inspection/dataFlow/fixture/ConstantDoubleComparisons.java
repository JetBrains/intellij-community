class Foo {

  private void foo() {
    double d = Double.MIN_VALUE;
    while (true) {
      if (d == Double.MIN_VALUE) {
        d = 0;
      }
    }
  }

  void equalZeroes() {
    double posz = +0.0, negz = -0.0;
    System.out.println(<warning descr="Condition 'posz == negz' is always 'true'">posz == negz</warning>);
    if (<warning descr="Condition '+0.0 == -0.0' is always 'true'">+0.0 == -0.0</warning>) {
      System.out.println("true!");
    }
  }
}