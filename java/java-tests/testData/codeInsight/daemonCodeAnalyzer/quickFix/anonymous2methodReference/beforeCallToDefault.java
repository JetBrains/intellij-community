// "Replace with method reference" "false"
class Test {
  interface InOut {
    void run() throws IOException;
    default void foo(){}
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