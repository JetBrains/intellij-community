class Example {
  void foo(final int a, final int b) {
    new Runnable() {
      @Override
      public void run() {
        bar(a, b);<caret>
      }
    };
  }

  void bar(int a, int b) {
  }
}