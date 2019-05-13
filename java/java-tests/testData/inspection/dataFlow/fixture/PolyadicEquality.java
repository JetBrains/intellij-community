// IDEA-193167
class Test {
  void test() {
    boolean b1 = true;
    boolean b2 = true;
    boolean b3 = false;
    System.out.println(<warning descr="Condition 'b1 == b2 == b3' is always 'false'"><warning descr="Condition 'b1 == b2' is always 'true'">b1 == b2</warning> == b3</warning>);
  }
}