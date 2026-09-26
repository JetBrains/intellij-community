public class ReturnOfInnerClass {

  public static Object zero() {
    return new Object() {};
  }

  public Object one() {
    <warning descr="Return of anonymous class instance">return</warning> new Object() {};
  }

  public Object two() {
    class A {}
    <warning descr="Return of local class 'A' instance">return</warning> new A();
  }

  class B {}
  public Object three() {
    <warning descr="Return of non-static inner class 'B' instance">return</warning> new B();
  }

  private Object four() {
    return new B();
  }

  protected Object five() {
    return new B();
  }

  Object six() {
    return new B();
  }
}