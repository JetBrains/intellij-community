class Test {
  void foo(final Runnable anObject) {
    Runnable c = new Runnable() {
      @Override
      public void run() {
          anObject.run();
      }
    };
  }
}