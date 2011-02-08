class A {
  B b1;
  static B b2;
  public class Z extends B{
    public void akshdkjasdh(){}
  }
  private class B{
    public void f() {}
  }
}


class C extends A {
  {
    b1.<ref>f();
  }
  }