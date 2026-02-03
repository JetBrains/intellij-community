import java.io.IOException;

class MyTest {
  public static void execute() throws IOException {
    try {
      throw new IOException();
    } catch(RuntimeException | IOException e) {
      var e1 = e;
      <error descr="Unhandled exception: java.lang.Exception">throw e1;</error>
    }
  }
}