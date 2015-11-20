class Test {
  interface I {
    void m();
  }

  <T> void call(T t) {}
  <T extends Runnable> void call1(T t) {}

  {
    call(<error descr="Object is not a functional interface">() -> {}</error>); 
    call1(() -> {}); 
  }
}
