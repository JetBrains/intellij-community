class Example {
  void foo(int a, int b) {
    new Runnable() {
      @Override
      public void run() {
        bar(<caret>);
      }
    };
  }

  void bar(int a, int b) {
  }
}