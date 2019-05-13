class Test {
  void foo() {
    Runnable c = new Runnable() {
      @Override
      public void run() {
        <selection>System.out.println();</selection>
      }
    };
  }
}