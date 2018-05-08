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
}