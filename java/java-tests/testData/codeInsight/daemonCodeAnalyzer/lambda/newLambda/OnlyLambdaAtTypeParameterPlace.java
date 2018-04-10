class Test {
  interface I {
    void m();
  }

  <T> void call(T t) {}
  <T extends Runnable> void call1(T t) {}

  {
    call(<error descr="Target type of a lambda conversion must be an interface">() -> {}</error>);
    call1(() -> {}); 
  }
}
