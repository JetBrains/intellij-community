class X {
  void test() {
    Integer integer = 0;
    ++integer;
    if (<warning descr="Condition 'integer == 1' is always 'true'">integer == 1</warning>) {
      System.out.println("Line to be printed");
    }
  }

  void test2() {
    Integer integer = 0;
    integer++;
    if (<warning descr="Condition 'integer == 1' is always 'true'">integer == 1</warning>) {
      System.out.println("Line to be printed");
    }
  }
}