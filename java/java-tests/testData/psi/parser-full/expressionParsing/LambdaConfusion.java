class X {
  private int foo(Runnable r) {
    return 1;
  }

  public int boo(int a) {
    return a;
  }

  public class Inner {
    public void run() {
      int a = ((X.this.boo(foo(() -> {
      }))));
    }
  }
}