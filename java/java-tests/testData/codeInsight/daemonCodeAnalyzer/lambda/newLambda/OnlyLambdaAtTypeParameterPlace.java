class Test {
  interface I {
    void m();
  }

  <T> void call(T t) {}
  <T extends Runnable> void call1(T t) {}

  {
    call<error descr="'call(T)' in 'Test' cannot be applied to '(<lambda expression>)'">(() -> {})</error>; 
    call1(() -> {}); 
  }
}
