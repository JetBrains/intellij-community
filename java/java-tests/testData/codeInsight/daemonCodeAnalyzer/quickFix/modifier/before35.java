// "Make 'e' final" "false"

class C {
  static {
    try {
      throw new Exception();
    }
    catch (RuntimeException | IOException e) {
      new Runnable() {
        public void run() {
          System.out.println(<caret>e);
        }
      }.run();
    }
  }
}