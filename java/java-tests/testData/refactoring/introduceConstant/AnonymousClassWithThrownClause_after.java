import java.io.IOException;
class Test {
    public static final I xxx = new I() {
      @Override
      public void foo() throws IOException {
        bar();
      }
    };

    void bar() throws IOException {}

  I get() {
    return xxx;
  }
}

interface I {
  void foo() throws IOException;
}