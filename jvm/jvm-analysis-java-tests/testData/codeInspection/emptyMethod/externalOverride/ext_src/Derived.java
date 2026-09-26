public class Derived extends Base {
  public void foo() {}
  public void bar() { System.out.println("Hello"); }
  public void foobar() { super.foobar(); }
}