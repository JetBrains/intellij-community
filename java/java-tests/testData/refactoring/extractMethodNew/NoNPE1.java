package extractMethod;

class Zzz<R> {

  protected void doAction(C c, boolean b) {
    if (b) {
      c.foo(<selection>() -> c.bar()</selection>);
    }
    else {
      c.foo(() -> c.bar());
    }
  }
  private class C {
    void foo(Runnable r) {}
    void bar() {}
  }
}