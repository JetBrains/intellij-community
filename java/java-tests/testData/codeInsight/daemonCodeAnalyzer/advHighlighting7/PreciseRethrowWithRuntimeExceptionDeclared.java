
import java.io.FileNotFoundException;
import java.io.IOException;

class MyTest {

  public void foo() {
    try {
      bar();
    }
    catch (FileNotFoundException e) {
      <error descr="Unhandled exception: java.io.FileNotFoundException">throw e;</error>
    }
    catch (IOException ignored) { }
  }

  private void bar() throws IOException, RuntimeException { }
}

