class BaseInner {
}

class Outer {
  public int x = 0;
  public void foo(){};

  public class Inner extends BaseInner {
    void b<caret>ar() {
      System.out.println(x);
      Outer.this.foo();
    }
  }
}