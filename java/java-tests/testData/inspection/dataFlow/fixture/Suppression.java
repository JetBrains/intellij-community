class Test {
  void noSuppress(int x) {
    if (<warning descr="Condition 'x < 0 && x > 20' is always 'false'">x < 0 && <warning descr="Condition 'x > 20' is always 'false' when reached">x > 20</warning></warning>) {}
    Object s = "23";
    System.out.println(((<warning descr="Casting 's' to 'Number' will produce 'ClassCastException' for any non-null value">Number</warning>)s).byteValue());
  }

  void suppressOld(int x) {
    //noinspection ConstantConditions
    if (x < 0 && x > 20) {}
    Object s = "23";
    //noinspection ConstantConditions
    System.out.println(((Number)s).byteValue());
  }

  void suppressNew(int x) {
    //noinspection ConstantValue
    if (x < 0 && x > 20) {}
    Object s = "23";
    //noinspection ConstantConditions
    System.out.println(((Number)s).byteValue());
  }

  void wrongSuppress(int x) {
    //noinspection ConstantValue
    if (x < 0 && x > 20) {}
    Object s = "23";
    //noinspection ConstantValue
    System.out.println(((<warning descr="Casting 's' to 'Number' will produce 'ClassCastException' for any non-null value">Number</warning>)s).byteValue());
  }
}