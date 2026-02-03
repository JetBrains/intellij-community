class Test {
  void f() {
    new Runnable() {
      public void run() {
        int j = 0;
        if (j == 0 && <selection>j < 0 && j > 0</selection>) {
          assert false;
        }
      }
    };
  }
}