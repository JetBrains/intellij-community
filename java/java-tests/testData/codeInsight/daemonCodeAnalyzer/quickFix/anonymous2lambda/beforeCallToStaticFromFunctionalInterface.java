// "Replace with lambda" "true"
class Test {
  interface InOut {
    void run() throws IOException;
    static void foo(){}
  }

  InOut bind() {
    return new In<caret>Out() {
      @Override
      public void run() throws IOException {
        foo();
      }
    };
  }
}