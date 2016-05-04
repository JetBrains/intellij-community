abstract class BaseInner {
    abstract void bar();
}

class Outer {
  public int x = 0;
  public void foo(){};

  public class Inner extends BaseInner {
    @Override
    void bar() {
      System.out.println(x);
      Outer.this.foo();
    }
  }
}