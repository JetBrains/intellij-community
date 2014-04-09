// "Replace with lambda" "true"
class Test {
  interface InOut {
    void run() throws IOException;
    default void foo(){}
  }

  interface InOutEx extends InOut {
    InOut bind() {
      return new In<caret>Out() {
        @Override
        public void run() throws IOException {
          foo();
        }
      };
    }
  }
}