import java.io.IOException;
class Test {

  I get() {
    return <selection>new I() {
      @Override
      public void foo() throws IOException {
      }
    }</selection>;
  }
}

interface I {
  void foo() throws IOException;
}