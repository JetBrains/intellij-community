class A {

  void m() {

    new Runnable() {
      public void run() {
        if (false) {
          run();
        }
      }
    };

  }

}