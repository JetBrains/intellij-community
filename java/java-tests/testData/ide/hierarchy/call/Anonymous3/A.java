class A {

  public void blabla() {
    final B b = new B();
    b.foo();
  }
}
class B {

  private final Runnable runnable;

  public B() {
    runnable = new Runnable() {
      @Override
      public void run() {
        foo();
      }
    };
  }

  public void foo() {
  }
}