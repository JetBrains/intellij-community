class EscapeAnalysis {
  void testSimple() {
    int[] x = new int[] {0};
    sideEffect();
    if(<warning descr="Condition 'x[0] == 1' is always 'false'">x[0] == 1</warning>) {
      System.out.println("Impossible");
    }
  }

  void testEscaped() {
    int[] x = new int[] {0};
    sideEffect(x);
    if(x[0] == 1) {
      System.out.println("Who knows?");
    }
  }

  void testEscapedAfterLoop() {
    int[] x;
    for (int i = 0; i < 10; i++) {
      x = new int[] {0};
      sideEffect();
      if(<warning descr="Condition 'x[0] == 1' is always 'false'">x[0] == 1</warning>) {
        System.out.println("Impossible");
      }
      sideEffect(x);
      if(x[0] == 1) {
        System.out.println("Who knows?");
      }
    }
  }

  native void sideEffect();
  native void sideEffect(int[] array);

  void testLambda() {
    int[] x = new int[] {0};
    Runnable r = () -> x[0] = 1;
    r.run();
    if(x[0] == 1) {
      System.out.println("ok");
    }
  }

  class X {
    X() {run();}

    void run() {};
  }


  void testClass() {
    int[] x = new int[] {0};
    new X() {
      void run() {
        x[0] = Math.random() > 0.5 ? 1 : 0;
      }
    };
    if(x[0] == 1) {
      System.out.println("possible");
    }
  }
}
