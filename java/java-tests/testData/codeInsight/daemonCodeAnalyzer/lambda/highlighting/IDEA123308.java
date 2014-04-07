class Test {
  private final int a;

  public Test() {
    a = 1;
    run(() -> {
      <error descr="Cannot assign a value to final variable 'a'">a</error> = 2;
    });
  }

  public void run(Runnable r) {
    r.run();
  }
}