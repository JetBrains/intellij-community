// "Make 'r' final" "false"

class C {
  void m() throws Exception {
    try (AutoCloseable r = null) {
      new Runnable() {
        public void run() {
          System.out.println(<caret>r);
        }
      }.run();
    }
  }
}