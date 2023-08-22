// "Replace with lambda" "true-preview"
class Test {
  interface InOut {
    void run() throws IOException;
    static void foo(){}
  }

  InOut bind() {
    return () -> InOut.foo();
  }
}