// "Replace with lambda" "true-preview"
class Test {
  interface InOut {
    void run() throws IOException;
    default void foo(){}
  }

  interface InOutEx extends InOut {
    InOut bind() {
      return () -> foo();
    }
  }
}