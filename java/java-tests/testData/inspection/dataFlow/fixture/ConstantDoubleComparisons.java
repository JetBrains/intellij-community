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

  void equalZeroesFloat() {
    float posz = +0.0f, negz = -0.0f;
    System.out.println(<warning descr="Condition 'posz == negz' is always 'true'">posz == negz</warning>);
    if (<warning descr="Condition '+0.0f == -0.0f' is always 'true'">+0.0f == -0.0f</warning>) {
      System.out.println("true!");
    }
  }

  void equalZeroesBoxed() {
    Double posz = +0.0, negz = -0.0;
    System.out.println(<warning descr="Result of 'posz.equals(negz)' is always 'false'">posz.equals(negz)</warning>);
  }

  void gtLt() {
    double x = 5.0;
    double y = 6.0;
    if (<warning descr="Condition 'x > y' is always 'false'">x > y</warning>) {}
    if (<warning descr="Condition 'x >= y' is always 'false'">x >= y</warning>) {}
    if (<warning descr="Condition 'x > 4' is always 'true'">x > 4</warning>) {}
    if (<warning descr="Condition 'y < 3' is always 'false'">y < 3</warning>) {}
  }

  void gtLtBoxed() {
    Double x = 5.0;
    Double y = 6.0;
    if (<warning descr="Condition 'x > y' is always 'false'">x > y</warning>) {}
    if (<warning descr="Condition 'x >= y' is always 'false'">x >= y</warning>) {}
    if (<warning descr="Condition 'x > 4' is always 'true'">x > 4</warning>) {}
    if (<warning descr="Condition 'y < 3' is always 'false'">y < 3</warning>) {}
  }
}