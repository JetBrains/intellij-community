class X {


  void x(int i) {
    String s = (<warning descr="Casting 'switch (i) {...}' to 'String' is redundant">String</warning>)switch (i) {
      case 1 -> "one";
      default -> "zero";
    };
    boolean b = (<warning descr="Casting '(new Object() instanceof String s1 && !s1.isEmpty())' to 'boolean' is redundant">boolean</warning>)(new Object() instanceof String s1 && !s1.isEmpty());
    int z = (<warning descr="Casting '(1 * 2 * )' to 'int' is redundant">int</warning>)(1 * 2 *<error descr="Expression expected">)</error>;
  }
}