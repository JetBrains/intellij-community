import java.io.IOException;
class Test {
  void bar() throws IOException {}

  I get() {
    return <selection>new I() {
      @Override
      public void foo() throws IOException {
        bar();
      }
    }</selection>;
  }
}

interface I {
  void foo() throws IOException;
}