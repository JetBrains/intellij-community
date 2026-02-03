class Test {
  void foo() {
    new Runnable() {
      public void run() {
        <selection>int i = 0;
        int j = 0;</selection>
        System.out.println(i + j);
      }
    }
  }
}