// "Replace with lambda" "true"
class Test {
  interface InOut {
    void run() throws IOException;
    static void foo(){}
  }

  InOut bind() {
    return () -> InOut.foo();
  }
}