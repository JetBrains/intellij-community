import java.util.function.Supplier;

class ABC {
  private <T> void <warning descr="Private method 'foo(java.util.function.Supplier<T>)' is never used">foo</warning>(Supplier<T> <warning descr="Parameter 'dictSeqs' is never used">dictSeqs</warning>) {
  }
  private void foo(Runnable <warning descr="Parameter 'r' is never used">r</warning>) {}

  {
    foo(() -> bar());
    foo(this::bar);
  }

  void bar(){}
}