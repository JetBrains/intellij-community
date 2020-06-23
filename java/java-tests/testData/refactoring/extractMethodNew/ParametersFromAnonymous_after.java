class Test {
  void f() {
    new Runnable() {
      public void run() {
        int j = 0;
        if (j == 0 && newMethod(j)) {
          assert false;
        }
      }
    };
  }

    private boolean newMethod(int j) {
        return j < 0 && j > 0;
    }
}