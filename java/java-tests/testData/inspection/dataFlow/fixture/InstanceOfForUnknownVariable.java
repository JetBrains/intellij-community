// IDEA-191374
class B {}
class A {
  private B b = new B();
  void foo() {
    String s = b != null ? b.toString() : ""; //comment this line and the error will appear

    if (<warning descr="Condition 'b instanceof B' is redundant and can be replaced with a null check">b instanceof B</warning>) {  // no error reported here
      System.out.println(b);
    }
  }

  interface Base {
    void doSmth();
  }
  interface Sub extends Base {}

  private static void test(Base[] operands) {
    operands[0].doSmth();
    if (operands[0] instanceof Sub) {
      System.out.println("possible");
    }
  }

  private static void test2(Sub[] operands) {
    operands[0].doSmth();
    if (<warning descr="Condition 'operands[0] instanceof Base' is always 'true'">operands[0] instanceof Base</warning>) {
      System.out.println("always");
    }
  }

}