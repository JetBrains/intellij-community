class Test {
  void foo(Object o) {
    try {
      Runnable r = () -> {
        <selection>System.out.println(o);</selection>
      };
    } catch (Throwable e) {}
  }
}