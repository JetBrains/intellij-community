package extractMethod;

import org.jetbrains.annotations.NotNull;

class Zzz<R> {

  protected void doAction(C c, boolean b) {
    if (b) {
      c.foo(newMethod(c));
    }
    else {
      c.foo(newMethod(c));
    }
  }

    @NotNull
    private Runnable newMethod(C c) {
        return () -> c.bar();
    }

    private class C {
    void foo(Runnable r) {}
    void bar() {}
  }
}