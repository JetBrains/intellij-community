interface Runnable {
  void run();
}

class A {
  void foo() {
    action(new Runnable() {
      public void run() {
        doIt();
      }
    });
  }

  void doIt() {}

  static void action(Runnable action) {
    action.run();
  }
}

abstract class Bar implements Runnable {
  Bar() {
    run();
  }
}