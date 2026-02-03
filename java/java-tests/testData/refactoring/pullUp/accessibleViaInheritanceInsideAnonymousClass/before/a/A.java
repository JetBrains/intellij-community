package a;
import b.B;
public class A extends B {
  void method2Move() {
    new I() {
      {
        super.foo();
        foo();
        bar();
      }
    }
  }

  protected static void bar(){}
  public static class I {
    protected void foo(){}
  }
}
